package rag_chatbot_application.service.impl;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import rag_chatbot_application.metrics.RagMetrics;
import rag_chatbot_application.model.Citation;
import rag_chatbot_application.model.RagAnswer;
import rag_chatbot_application.model.SearchResult;
import rag_chatbot_application.service.CitationMapper;
import rag_chatbot_application.service.RagService;
import rag_chatbot_application.service.ResilientChatService;
import rag_chatbot_application.service.VectorStoreService;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Idiomatic Spring AI implementation of the RAG loop.
 * Uses QuestionAnswerAdvisor to automatically retrieve context and augment the prompt,
 * with the Phase 7 resilient fallback chain applied to generation.
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
    private final ResilientChatService resilientChatService;
    private final RagMetrics ragMetrics;
    private final int topK;
    private final double threshold;

    public AdvisorRagService(ChatClient.Builder chatClientBuilder,
                             VectorStore vectorStore,
                             VectorStoreService vectorStoreService,
                             ResilientChatService resilientChatService,
                             RagMetrics ragMetrics,
                             @Value("${rag.retrieval.top-k:6}") int topK,
                             @Value("${rag.retrieval.similarity-threshold:0.5}") double threshold) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.vectorStoreService = vectorStoreService;
        this.resilientChatService = resilientChatService;
        this.ragMetrics = ragMetrics;
        this.topK = topK;
        this.threshold = threshold;
    }

    @Override
    public RagAnswer answer(String question) {
        ragMetrics.incQuery();
        // Generation (advisor retrieves + augments internally) goes through the fallback chain
        String answer = resilientChatService.call(model ->
                chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(question)
                        .advisors(buildAdvisor())
                        .options(GoogleGenAiChatOptions.builder().model(model))
                        .call()
                        .content());

        // Reuse the same retrieval for citations
        List<SearchResult> sources = vectorStoreService.search(question, topK, threshold);
        List<Citation> citations = CitationMapper.from(sources);

        return new RagAnswer(answer, citations);
    }

    @Override
    public Flux<String> streamAnswer(String question) {
        return resilientChatService.stream(model ->
                chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(question)
                        .advisors(buildAdvisor())
                        .options(GoogleGenAiChatOptions.builder().model(model))
                        .stream()
                        .content());
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