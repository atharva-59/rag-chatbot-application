package rag_chatbot_application.model;

import java.util.List;

/**
 * Non-streaming RAG answer with the sources used to ground it.
 */
public record RagAnswer(
        String answer,
        List<Citation> citations
) {
}
