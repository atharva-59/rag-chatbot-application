package rag_chatbot_application.service.impl;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import rag_chatbot_application.model.Citation;
import rag_chatbot_application.model.RagAnswer;
import rag_chatbot_application.model.SearchResult;
import rag_chatbot_application.service.CitationMapper;
import rag_chatbot_application.service.RagService;
import rag_chatbot_application.service.VectorStoreService;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Idiomatic Spring AI implementation of the RAG loop.
 * Uses QuestionAnswerAdvisor to automatically retrieve context and augment the prompt.
 * This is the production default.
 */
@Service
@ConditionalOnProperty(name = "rag.mode", havingValue = "advisor", matchIfMissing = true)
public class AdvisorRagService implements RagService {

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant that answers questions using ONLY the provided context.
            If the answer is not contained in the context, say clearly:
            "I don't have enough information in the knowledge base to answer that."
            Do not make up facts. Be concise and cite relevant details from the context.
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final VectorStoreService vectorStoreService;
    private final int topK;
    private final double threshold;

    public AdvisorRagService(ChatClient.Builder chatClientBuilder,
                             VectorStore vectorStore,
                             VectorStoreService vectorStoreService,
                             @Value("${rag.retrieval.top-k:6}") int topK,
                             @Value("${rag.retrieval.similarity-threshold:0.5}") double threshold) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.vectorStoreService = vectorStoreService;
        this.topK = topK;
        this.threshold = threshold;
    }

    @Override
    public RagAnswer answer(String question) {
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(question)
                .advisors(buildAdvisor())
                .call()
                .content();

        // Reuse the same retrieval for citations
        List<SearchResult> sources =
                vectorStoreService.search(question, topK, threshold);
        List<Citation> citations =
                CitationMapper.from(sources);

        return new RagAnswer(answer, citations);
    }

    @Override
    public Flux<String> streamAnswer(String question) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(question)
                .advisors(buildAdvisor())
                .stream()
                .content();
    }

    /** Builds a retrieval advisor that injects top-K context into the prompt. */
    private QuestionAnswerAdvisor buildAdvisor() {
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(topK)
                .similarityThreshold(threshold)
                .build();

        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .build();
    }
}