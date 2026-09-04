package rag_chatbot_application.exception;

/**
 * Thrown when every model in the fallback chain has been rate-limited or failed.
 */
public class AllModelsExhaustedException extends RuntimeException {
    public AllModelsExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}