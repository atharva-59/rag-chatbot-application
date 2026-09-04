package rag_chatbot_application.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import rag_chatbot_application.exception.DocumentIngestionException;
import rag_chatbot_application.model.IngestResponse;
import rag_chatbot_application.service.IngestionService;
import rag_chatbot_application.service.VectorStoreService;
import rag_chatbot_application.service.WebPageFetcher;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class IngestionServiceImpl implements IngestionService {

    private static final Logger log =
            LoggerFactory.getLogger(IngestionServiceImpl.class);

    private final VectorStoreService vectorStoreService;
    private final WebPageFetcher webPageFetcher;
    private final int chunkSize;

    public IngestionServiceImpl(
            VectorStoreService vectorStoreService, WebPageFetcher webPageFetcher,
            @Value("${rag.chunking.chunk-size:800}") int chunkSize) {

        this.vectorStoreService = vectorStoreService;
        this.webPageFetcher = webPageFetcher;


        this.chunkSize = chunkSize;
    }

    // ---------- PDF ----------
    @Override
    public IngestResponse ingestPdf(MultipartFile file) {
        validatePdf(file);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.pdf";
        try {
            Resource resource = new InputStreamResource(file.getInputStream()) {
                @Override public String getFilename() { return filename; }
                @Override public long contentLength() { return file.getSize(); }
            };
            PagePdfDocumentReader reader = new PagePdfDocumentReader(
                    resource,
                    PdfDocumentReaderConfig.builder().withPagesPerDocument(1).build());
            List<Document> pages = reader.get();

            List<Document> chunks = split(pages);
            chunks.forEach(c -> c.getMetadata().put("source", filename));

            int stored = vectorStoreService.storeDocuments(chunks);
            log.info("Ingested PDF '{}' -> {} pages, {} chunks", filename, pages.size(), stored);
            return new IngestResponse(filename, pages.size(), stored);

        } catch (IOException e) {
            throw new DocumentIngestionException(
                    "Could not read the uploaded PDF: " + e.getMessage(), e);
        }
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DocumentIngestionException("File is required and must not be empty", null);
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".pdf")) {
            throw new DocumentIngestionException("Only .pdf files are supported", null);
        }
    }


    @Override
    public IngestResponse ingestUrl(String url) {
        WebPageFetcher.FetchedPage page = webPageFetcher.fetch(url);

        // Wrap the extracted text in a single Document, then split it
        Document pageDoc = new Document(page.text(), Map.of(
                "source", url,
                "title", page.title() != null ? page.title() : ""));

        List<Document> chunks = split(List.of(pageDoc));
        // Ensure metadata survives on every chunk
        chunks.forEach(c -> {
            c.getMetadata().putIfAbsent("source", url);
            c.getMetadata().putIfAbsent("title", page.title() != null ? page.title() : "");
        });

        int stored = vectorStoreService.storeDocuments(chunks);
        log.info("Ingested URL '{}' -> {} chunks", url, stored);
        return new IngestResponse(url, 1, stored);
    }

    // ---------- Shared helpers ----------

    private List<Document> split(List<Document> docs) {

        TokenTextSplitter splitter =
                TokenTextSplitter.builder()
                        .withChunkSize(chunkSize)
                        .build();

        return splitter.apply(docs);
    }

}