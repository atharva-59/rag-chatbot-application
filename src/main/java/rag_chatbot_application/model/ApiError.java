package rag_chatbot_application.model;

import java.time.Instant;

/**
 * Consistent error response returned by the global exception handler.
 */
public record ApiError(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(status, error, message, path, Instant.now());
    }
}