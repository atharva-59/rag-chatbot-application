package rag_chatbot_application.service.impl;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import rag_chatbot_application.model.Citation;
import rag_chatbot_application.model.RagAnswer;
import rag_chatbot_application.model.SearchResult;
import rag_chatbot_application.service.CitationMapper;
import rag_chatbot_application.service.RagService;
import rag_chatbot_application.service.RetrievalService;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@ConditionalOnProperty(name = "rag.mode", havingValue = "manual")
public class ManualRagService implements RagService {

    private static final Logger log = LoggerFactory.getLogger(ManualRagService.class);

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant that answers questions using ONLY the provided context.
            If the answer is not contained in the context, say clearly:
            "I don't have enough information in the knowledge base to answer that."
            Do not make up facts. Be concise and cite relevant details from the context.
            """;

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;

    public ManualRagService(ChatClient.Builder chatClientBuilder,
                            RetrievalService retrievalService) {
        this.chatClient = chatClientBuilder.build();
        this.retrievalService = retrievalService;
    }

    @Override
    public RagAnswer answer(String question) {
        // ---------- STAGE 1: RETRIEVE (adaptive) ----------
        log.info("=== RAG STAGE 1: RETRIEVE ===");
        log.info("Question: '{}'", question);
        List<SearchResult> hits = retrievalService.retrieve(question);
        log.info("Final retrieved chunk count: {}", hits.size());

        IntStream.range(0, hits.size()).forEach(i -> {
            SearchResult r = hits.get(i);
            String preview = r.content().length() > 120
                    ? r.content().substring(0, 120) + "..." : r.content();
            log.info("  [chunk {}] score={} source={} :: {}",
                    i + 1, r.score(),
                    r.metadata() != null ? r.metadata().get("source") : "n/a", preview);
        });

        // ---------- STAGE 2: AUGMENT ----------
        log.info("=== RAG STAGE 2: AUGMENT ===");
        String augmentedUser = buildAugmentedPrompt(question, hits);
        log.info("Augmented prompt sent to the model:\n{}", augmentedUser);

        // ---------- STAGE 3: GENERATE ----------
        log.info("=== RAG STAGE 3: GENERATE ===");
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(augmentedUser)
                .call()
                .content();
        log.info("Model answer: {}", answer);

        // ---------- STAGE 4: CITATIONS ----------
        List<Citation> citations = CitationMapper.from(hits);
        log.info("=== RAG COMPLETE ({} citations) ===", citations.size());

        return new RagAnswer(answer, citations);
    }

    @Override
    public Flux<String> streamAnswer(String question) {
        log.info("=== RAG STREAM: RETRIEVE for '{}' ===", question);
        List<SearchResult> hits = retrievalService.retrieve(question);
        String augmentedUser = buildAugmentedPrompt(question, hits);

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(augmentedUser)
                .stream()
                .content();
    }

    private String buildAugmentedPrompt(String question, List<SearchResult> hits) {
        String context = hits.stream()
                .map(SearchResult::content)
                .collect(Collectors.joining("\n---\n"));

        return """
                Answer the question using ONLY the context below.
                If the answer isn't in the context, say you don't know.

                CONTEXT:
                %s

                QUESTION:
                %s
                """.formatted(context.isBlank() ? "(no relevant context found)" : context, question);
    }
}