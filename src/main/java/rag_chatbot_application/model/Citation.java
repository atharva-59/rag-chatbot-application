package rag_chatbot_application.model;

/**
 * A clean, user-facing citation for a piece of retrieved context.
 *
 * @param source  origin (filename or URL)
 * @param title   optional page/document title
 * @param snippet a short preview of the cited chunk
 * @param score   similarity score of the chunk
 */
public record Citation(
        String source,
        String title,
        String snippet,
        Double score
) {
}