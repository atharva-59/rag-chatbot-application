package rag_chatbot_application.service.impl;

//import com.example.ragchatbot.model.Citation;
//import com.example.ragchatbot.model.RagAnswer;
//import com.example.ragchatbot.model.SearchResult;
//import com.example.ragchatbot.service.CitationMapper;
//import com.example.ragchatbot.service.RagService;
//import com.example.ragchatbot.service.ResilientChatService;
//import com.example.ragchatbot.service.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import rag_chatbot_application.metrics.RagMetrics;
import rag_chatbot_application.model.Citation;
import rag_chatbot_application.model.RagAnswer;
import rag_chatbot_application.model.SearchResult;
import rag_chatbot_application.service.CitationMapper;
import rag_chatbot_application.service.RagService;
import rag_chatbot_application.service.ResilientChatService;
import rag_chatbot_application.service.RetrievalService;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

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
    private final ResilientChatService resilientChatService;
    private final RagMetrics ragMetrics;

    public ManualRagService(ChatClient.Builder chatClientBuilder,
                            RetrievalService retrievalService,
                            ResilientChatService resilientChatService,
                            RagMetrics ragMetrics) {
        this.chatClient = chatClientBuilder.build();
        this.retrievalService = retrievalService;
        this.resilientChatService = resilientChatService;
        this.ragMetrics = ragMetrics;
    }

    @Override
    public RagAnswer answer(String question) {
        ragMetrics.incQuery();
        List<SearchResult> hits = retrievalService.retrieve(question);
        String augmentedUser = buildAugmentedPrompt(question, hits);

        // Generation now goes through the resilient fallback chain
        String answer = resilientChatService.call(model ->
                chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(augmentedUser)
                        .options(GoogleGenAiChatOptions.builder().model(model))
                        .call()
                        .content());

        List<Citation> citations = CitationMapper.from(hits);
        return new RagAnswer(answer, citations);
    }

    @Override
    public Flux<String> streamAnswer(String question) {
        List<SearchResult> hits = retrievalService.retrieve(question);
        String augmentedUser = buildAugmentedPrompt(question, hits);

        return resilientChatService.stream(model ->
                chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(augmentedUser)
                        .options(GoogleGenAiChatOptions.builder().model(model))
                        .stream()
                        .content());
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