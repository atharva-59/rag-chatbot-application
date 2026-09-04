package rag_chatbot_application.service.impl;

//import com.example.ragchatbot.exception.AllModelsExhaustedException;
//import com.example.ragchatbot.service.ResilientChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import rag_chatbot_application.exception.AllModelsExhaustedException;
import rag_chatbot_application.service.ResilientChatService;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Function;

@Service
public class ResilientChatServiceImpl implements ResilientChatService {

    private static final Logger log = LoggerFactory.getLogger(ResilientChatServiceImpl.class);

    private final List<String> models;
    private final int maxRetries;
    private final long backoffMs;

    public ResilientChatServiceImpl(
            @Value("${rag.models.fallback-chain:gemini-2.5-flash,gemini-2.5-flash-lite}") String chain,
            @Value("${rag.resilience.max-retries:2}") int maxRetries,
            @Value("${rag.resilience.backoff-ms:800}") long backoffMs) {
        this.models = List.of(chain.split("\\s*,\\s*"));
        this.maxRetries = maxRetries;
        this.backoffMs = backoffMs;
        log.info("Resilient chat configured with model chain: {}", models);
    }

    @Override
    public String call(Function<String, String> callWithModel) {
        Throwable lastError = null;

        for (String model : models) {
            log.info("Attempting model '{}'", model);
            try {
                return callWithRetry(model, callWithModel);
            } catch (NonTransientAiException e) {
                // e.g. bad API key / invalid config — retrying/falling back won't help
                log.error("Non-transient error on model '{}': {}", model, e.getMessage());
                throw e;
            } catch (TransientAiException e) {
                // e.g. HTTP 429 rate limit or transient failure — try the next model
                log.warn("Model '{}' rate-limited/transient failure, falling back. ({})",
                        model, e.getMessage());
                lastError = e;
            } catch (Exception e) {
                log.warn("Model '{}' failed unexpectedly, falling back. ({})", model, e.getMessage());
                lastError = e;
            }
        }

        throw new AllModelsExhaustedException(
                "All configured models are unavailable or rate-limited. Please try again later.",
                lastError);
    }

    /** Retries a single model with exponential backoff on transient errors. */
    private String callWithRetry(String model, Function<String, String> callWithModel) {
        int attempt = 0;
        while (true) {
            try {
                return callWithModel.apply(model);
            } catch (TransientAiException e) {
                attempt++;
                if (attempt > maxRetries) {
                    throw e; // give up on this model -> caller will fall back
                }
                long wait = backoffMs * (long) Math.pow(2, attempt - 1);
                log.info("Retry {}/{} for model '{}' after {}ms", attempt, maxRetries, model, wait);
                sleep(wait);
            }
        }
    }

    @Override
    public Flux<String> stream(Function<String, Flux<String>> streamWithModel) {
        // For streaming we use the primary model; on transient error, fall back to the next.
        return attemptStream(0, streamWithModel);
    }

    private Flux<String> attemptStream(int index, Function<String, Flux<String>> streamWithModel) {
        if (index >= models.size()) {
            return Flux.error(new AllModelsExhaustedException(
                    "All configured models are unavailable or rate-limited.", null));
        }
        String model = models.get(index);
        log.info("Streaming with model '{}'", model);
        return streamWithModel.apply(model)
                .onErrorResume(TransientAiException.class, e -> {
                    log.warn("Stream model '{}' transient failure, falling back. ({})",
                            model, e.getMessage());
                    return attemptStream(index + 1, streamWithModel);
                });
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}