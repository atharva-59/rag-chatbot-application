package rag_chatbot_application.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import rag_chatbot_application.model.SearchResult;
import rag_chatbot_application.service.VectorStoreService;

import java.util.List;

@Service
public class VectorStoreServiceImpl implements VectorStoreService {

    private static final Logger log =
            LoggerFactory.getLogger(VectorStoreServiceImpl.class);

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    private final int batchSize;
    private final int maxRetries;
    private final long initialBackoffMs;

    public VectorStoreServiceImpl(
            VectorStore vectorStore,
            EmbeddingModel embeddingModel,
            @Value("${rag.embedding.batch-size:5}") int batchSize,
            @Value("${rag.embedding.max-retries:3}") int maxRetries,
            @Value("${rag.embedding.initial-backoff-ms:2000}") long initialBackoffMs) {

        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
    }

    @Override
    public int store(List<String> texts) {

        if (texts == null || texts.isEmpty()) {
            return 0;
        }

        List<Document> documents = texts.stream()
                .map(Document::new)
                .toList();

        return storeDocuments(documents);
    }

    @Override
    public int storeDocuments(List<Document> documents) {

        if (documents == null || documents.isEmpty()) {
            return 0;
        }

        int totalDocuments = documents.size();
        int totalBatches =
                (totalDocuments + batchSize - 1) / batchSize;

        int stored = 0;

        for (int start = 0; start < totalDocuments; start += batchSize) {

            int end = Math.min(start + batchSize, totalDocuments);

            List<Document> batch = documents.subList(start, end);

            int batchNumber = (start / batchSize) + 1;

            log.info(
                    "Processing embedding batch {}/{} with {} documents",
                    batchNumber,
                    totalBatches,
                    batch.size()
            );

            storeBatchWithRetry(batch, batchNumber, totalBatches);

            stored += batch.size();

            log.info(
                    "Successfully stored batch {}/{}",
                    batchNumber,
                    totalBatches
            );
        }

        log.info(
                "Successfully stored {} documents in {} batches",
                stored,
                totalBatches
        );

        return stored;
    }

    private void storeBatchWithRetry(
            List<Document> batch,
            int batchNumber,
            int totalBatches) {

        int attempt = 0;

        while (true) {

            try {

                vectorStore.add(batch);

                return;

            } catch (RuntimeException e) {

                attempt++;

                if (!isQuotaOrRateLimitError(e)) {

                    log.error(
                            "Embedding failed for batch {}/{}",
                            batchNumber,
                            totalBatches,
                            e
                    );

                    throw e;
                }

                if (attempt > maxRetries) {

                    log.error(
                            "Embedding batch {}/{} failed after {} retries",
                            batchNumber,
                            totalBatches,
                            maxRetries,
                            e
                    );

                    throw e;
                }

                long backoffMs =
                        initialBackoffMs * (1L << (attempt - 1));

                log.warn(
                        "Gemini quota/rate limit encountered for batch {}/{}. " +
                                "Retrying attempt {}/{} after {} ms",
                        batchNumber,
                        totalBatches,
                        attempt,
                        maxRetries,
                        backoffMs
                );

                sleep(backoffMs);
            }
        }
    }

    private boolean isQuotaOrRateLimitError(Throwable throwable) {

        Throwable current = throwable;

        while (current != null) {

            String message = current.getMessage();

            if (message != null) {

                String lowerMessage =
                        message.toLowerCase();

                if (lowerMessage.contains("429")
                        || lowerMessage.contains("quota")
                        || lowerMessage.contains("rate limit")
                        || lowerMessage.contains("resource exhausted")) {

                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }

    private void sleep(long milliseconds) {

        try {

            Thread.sleep(milliseconds);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Embedding retry interrupted",
                    e
            );
        }
    }

    @Override
    public List<SearchResult> search(
            String query,
            int topK,
            double threshold) {

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();

        List<Document> hits =
                vectorStore.similaritySearch(request);

        if (hits == null) {
            return List.of();
        }

        log.info(
                "Similarity search for '{}' returned {} hits",
                query,
                hits.size()
        );

        return hits.stream()
                .map(doc -> new SearchResult(
                        doc.getText(),
                        doc.getScore(),
                        doc.getMetadata()))
                .toList();
    }

    @Override
    public int embeddingDimension() {

        float[] vector =
                embeddingModel.embed("dimension probe");

        return vector.length;
    }
}