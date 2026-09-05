package rag_chatbot_application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Central Micrometer counters for key RAG operations.
 * Exposed under /actuator/metrics (e.g. rag.queries, rag.ingested, rag.fallbacks).
 */
@Component
public class RagMetrics {

    private final Counter queries;
    private final Counter ingested;
    private final Counter fallbacks;

    public RagMetrics(MeterRegistry registry) {
        this.queries = Counter.builder("rag.queries")
                .description("Number of RAG questions answered").register(registry);
        this.ingested = Counter.builder("rag.ingested")
                .description("Number of documents ingested").register(registry);
        this.fallbacks = Counter.builder("rag.fallbacks")
                .description("Number of times a model fallback was triggered").register(registry);
    }

    public void incQuery()    { queries.increment(); }
    public void incIngested() { ingested.increment(); }
    public void incFallback() { fallbacks.increment(); }
}