package rag_chatbot_application.service;

import reactor.core.publisher.Flux;

/**
 * Service contract for talking to the Gemini chat model.
 */
public interface ChatService {

    /** Sends a prompt and returns the full reply (blocking). */
    String chat(String prompt);

    /** Sends a prompt and streams the reply token-by-token. */
    Flux<String> streamChat(String prompt);
}