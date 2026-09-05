package rag_chatbot_application.health;

//import org.springframework.boot.actuate.health.Health;
//import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reports whether Postgres + the pgvector store are reachable.
 * Surfaces under /actuator/health as "vectorStore".
 */
@Component("vectorStoreHealth")
public class VectorStoreHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    public VectorStoreHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            // Simple connectivity + pgvector presence check
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            Integer vectorExt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);

            if (one != null && one == 1 && vectorExt != null && vectorExt > 0) {
                return Health.up()
                        .withDetail("database", "reachable")
                        .withDetail("pgvector", "installed")
                        .build();
            }
            return Health.down()
                    .withDetail("database", "reachable")
                    .withDetail("pgvector", vectorExt != null && vectorExt > 0 ? "installed" : "missing")
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("database", "unreachable")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}