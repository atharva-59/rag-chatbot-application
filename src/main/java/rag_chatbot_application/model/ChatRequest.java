package rag_chatbot_application.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for the chat endpoints.
 */
public record ChatRequest(
        @NotBlank(message = "prompt must not be blank")
        @Size(max = 2000, message = "prompt must be at most 2000 characters")
        String prompt
) {
}