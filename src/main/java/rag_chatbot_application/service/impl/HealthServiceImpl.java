package rag_chatbot_application.service.impl;

//import com.example.ragchatbot.model.HealthResponse;
//import com.example.ragchatbot.service.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import rag_chatbot_application.model.HealthResponse;
import rag_chatbot_application.service.HealthService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;

@Service
public class HealthServiceImpl implements HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthServiceImpl.class);

    private final DataSource dataSource;
    private final String applicationName;

    public HealthServiceImpl(DataSource dataSource,
                             @Value("${spring.application.name:rag-chatbot}") String applicationName) {
        this.dataSource = dataSource;
        this.applicationName = applicationName;
    }

    @Override
    public HealthResponse checkHealth() {
        String dbStatus = isDatabaseReachable() ? "UP" : "DOWN";
        String overall = "UP".equals(dbStatus) ? "UP" : "DEGRADED";

        return new HealthResponse(
                overall,
                applicationName,
                dbStatus,
                Instant.now()
        );
    }

    /**
     * Attempts to obtain a live connection and validate it.
     * Returns false (never throws) so the health endpoint stays resilient.
     */
    private boolean isDatabaseReachable() {
        try (Connection connection = dataSource.getConnection()) {
            // isValid runs a lightweight check with a 2-second timeout
            return connection.isValid(2);
        } catch (Exception e) {
            log.warn("Database health check failed: {}", e.getMessage());
            return false;
        }
    }
}