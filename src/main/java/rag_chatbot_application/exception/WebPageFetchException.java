package rag_chatbot_application.exception;

/**
 * Thrown when a web page cannot be fetched or yields no usable text.
 * Carries the HTTP status the API should return to the client.
 */
public class WebPageFetchException extends RuntimeException {

    private final int status;

    public WebPageFetchException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
