package rag_chatbot_application.service;

//import com.example.ragchatbot.model.HealthResponse;

import rag_chatbot_application.model.HealthResponse;

/**
 * Service contract for application health checks.
 * Keeping an interface + impl separation for testability and clean architecture.
 */
public interface HealthService {

    /**
     * Builds the current health status of the application,
     * including database connectivity.
     */
    HealthResponse checkHealth();
}