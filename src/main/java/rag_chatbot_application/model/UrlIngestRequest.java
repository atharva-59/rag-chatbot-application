package rag_chatbot_application.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for URL ingestion.
 */
public record UrlIngestRequest(
        @NotBlank(message = "url must not be blank")
        @Pattern(regexp = "^https?://.+", message = "url must start with http:// or https://")
        String url
) {
}