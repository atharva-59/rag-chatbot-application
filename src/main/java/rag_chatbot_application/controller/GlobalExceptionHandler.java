package rag_chatbot_application.controller;

//import com.example.ragchatbot.exception.AllModelsExhaustedException;
//import com.example.ragchatbot.model.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import rag_chatbot_application.exception.AllModelsExhaustedException;
import rag_chatbot_application.exception.DocumentIngestionException;
import rag_chatbot_application.exception.WebPageFetchException;
import rag_chatbot_application.model.ApiError;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** All fallback models exhausted -> 429 with a retry hint. */
    @ExceptionHandler(AllModelsExhaustedException.class)
    public ResponseEntity<ApiError> handleExhausted(AllModelsExhaustedException e, HttpServletRequest req) {
        log.warn("All models exhausted: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiError.of(429, "Too Many Requests",
                        e.getMessage(), req.getRequestURI()));
    }

    /** Validation failures (@Valid) -> 400. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", msg, req.getRequestURI()));
    }

    /** Oversized uploads -> 413. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadSize(MaxUploadSizeExceededException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of(413, "Payload Too Large",
                        "Uploaded file exceeds the allowed size.", req.getRequestURI()));
    }

    /** ResponseStatusException thrown by services (e.g. bad URL/PDF) -> its status. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException e, HttpServletRequest req) {
        return ResponseEntity.status(e.getStatusCode())
                .body(ApiError.of(e.getStatusCode().value(),
                        e.getStatusCode().toString(),
                        e.getReason() != null ? e.getReason() : "Request failed",
                        req.getRequestURI()));
    }

    /**
     * PDF/document ingestion failures -> 422.
     */
    @ExceptionHandler(DocumentIngestionException.class)
    public ResponseEntity<ApiError> handleDocumentIngestion(
            DocumentIngestionException e,
            HttpServletRequest req) {

        log.warn("Document ingestion failed at {}: {}", req.getRequestURI(), e.getMessage());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(
                        HttpStatus.UNPROCESSABLE_ENTITY.value(),
                        "Unprocessable Entity",
                        e.getMessage(),
                        req.getRequestURI()
                ));
    }

    /**
     * URL fetch/extraction failures -> status determined by WebPageFetchException.
     */
    @ExceptionHandler(WebPageFetchException.class)
    public ResponseEntity<ApiError> handleWebPageFetch(
            WebPageFetchException e,
            HttpServletRequest req) {

        log.warn(
                "URL processing failed at {}: {}",
                req.getRequestURI(),
                e.getMessage()
        );

        return ResponseEntity.status(e.getStatus())
                .body(ApiError.of(
                        e.getStatus(),
                        HttpStatus.valueOf(e.getStatus()).getReasonPhrase(),
                        e.getMessage(),
                        req.getRequestURI()
                ));
    }

    /** Anything else -> 500 (no stack trace leaked). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception e, HttpServletRequest req) {
        log.error("Unhandled error at {}: {}", req.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Internal Server Error",
                        "An unexpected error occurred.", req.getRequestURI()));
    }
}
