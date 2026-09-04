package rag_chatbot_application.service;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * Wraps chat calls with a model fallback chain + retry/backoff.
 * Callers provide a function that, given a model name, performs the actual call.
 */
public interface ResilientChatService {

    /** Blocking call with fallback across the configured model chain. */
    String call(Function<String, String> callWithModel);

    /** Streaming call using the first model in the chain (fallback applies to setup errors). */
    Flux<String> stream(Function<String, Flux<String>> streamWithModel);
}