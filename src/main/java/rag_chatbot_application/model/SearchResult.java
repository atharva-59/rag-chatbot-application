package rag_chatbot_application.model;

import java.util.Map;

/**
 * Immutable DTO representing a single similarity-search hit.
 *
 * @param content  the chunk text
 * @param score    similarity score (higher = more relevant)
 * @param metadata document metadata (e.g. source, distance)
 */
public record SearchResult(
        String content,
        Double score,
        Map<String, Object> metadata
) {
}