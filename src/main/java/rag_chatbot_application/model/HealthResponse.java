package rag_chatbot_application.model;

import java.time.Instant;

/**
 * Immutable response model for the health endpoint.
 *
 * @param status      overall application status (e.g. "UP" / "DEGRADED")
 * @param application the application name
 * @param database    database connectivity status ("UP" / "DOWN")
 * @param timestamp   time the health check was performed
 */
public record HealthResponse(
        String status,
        String application,
        String database,
        Instant timestamp
) {
}