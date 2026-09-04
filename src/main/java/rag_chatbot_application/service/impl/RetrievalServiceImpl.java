package rag_chatbot_application.service.impl;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import rag_chatbot_application.model.SearchResult;
import rag_chatbot_application.service.RetrievalService;
import rag_chatbot_application.service.VectorStoreService;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RetrievalServiceImpl implements RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalServiceImpl.class);

    /** Keywords that signal a broad / summary-style question. */
    private static final Set<String> BROAD_KEYWORDS = Set.of(
            "summarize", "summary", "overview", "explain everything", "the whole",
            "all of", "in general", "what is this", "what are the main",
            "key points", "tell me about", "describe the document", "high level"
    );

    private final VectorStoreService vectorStoreService;

    private final int specificTopK;
    private final double specificThreshold;
    private final int broadTopK;
    private final double broadThreshold;
    private final int subQueries;
    private final boolean fanoutEnabled;

    public RetrievalServiceImpl(
            VectorStoreService vectorStoreService,
            @Value("${rag.retrieval.specific.top-k:6}") int specificTopK,
            @Value("${rag.retrieval.specific.similarity-threshold:0.5}") double specificThreshold,
            @Value("${rag.retrieval.broad.top-k:20}") int broadTopK,
            @Value("${rag.retrieval.broad.similarity-threshold:0.0}") double broadThreshold,
            @Value("${rag.retrieval.broad.sub-queries:3}") int subQueries,
            @Value("${rag.retrieval.fanout-enabled:true}") boolean fanoutEnabled) {
        this.vectorStoreService = vectorStoreService;
        this.specificTopK = specificTopK;
        this.specificThreshold = specificThreshold;
        this.broadTopK = broadTopK;
        this.broadThreshold = broadThreshold;
        this.subQueries = subQueries;
        this.fanoutEnabled = fanoutEnabled;
    }

    @Override
    public List<SearchResult> retrieve(String question) {
        boolean broad = fanoutEnabled && isBroadQuery(question);
        log.info("Query classified as: {}", broad ? "BROAD (fan-out)" : "SPECIFIC (focused)");

        List<SearchResult> results = broad
                ? retrieveBroad(question)
                : retrieveSpecific(question);

        List<SearchResult> deduped = deduplicate(results);
        log.info("Retrieved {} raw -> {} after dedup", results.size(), deduped.size());
        return deduped;
    }

    // ---------- SPECIFIC: single focused search ----------
    private List<SearchResult> retrieveSpecific(String question) {
        log.info("Specific search -> topK={}, threshold={}", specificTopK, specificThreshold);
        return vectorStoreService.search(question, specificTopK, specificThreshold);
    }

    // ---------- BROAD: fan out into sub-queries, merge ----------
    private List<SearchResult> retrieveBroad(String question) {
        List<String> queries = buildSubQueries(question);
        log.info("Broad fan-out into {} sub-queries: {}", queries.size(), queries);

        List<SearchResult> merged = new ArrayList<>();
        int perQueryK = Math.max(1, broadTopK / queries.size());
        for (String q : queries) {
            List<SearchResult> hits = vectorStoreService.search(q, perQueryK, broadThreshold);
            log.info("  sub-query '{}' -> {} hits", q, hits.size());
            merged.addAll(hits);
        }
        return merged;
    }

    /**
     * Turns a broad question into multiple focused sub-queries.
     * Heuristic (no extra LLM call): original + generic coverage angles.
     * Can later be replaced with an LLM-generated multi-query for even better spread.
     */
    private List<String> buildSubQueries(String question) {
        List<String> subs = new ArrayList<>();
        subs.add(question);
        subs.add("main topics and key points");
        subs.add("important details, definitions and conclusions");
        return subs.stream().limit(Math.max(1, subQueries)).collect(Collectors.toList());
    }

    // ---------- classification ----------
    private boolean isBroadQuery(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        return BROAD_KEYWORDS.stream().anyMatch(q::contains);
    }

    // ---------- dedup + rank ----------
    private List<SearchResult> deduplicate(List<SearchResult> results) {
        Map<String, SearchResult> unique = new LinkedHashMap<>();
        for (SearchResult r : results) {
            String key = fingerprint(r.content());
            // keep the highest-scoring instance of duplicate content
            SearchResult existing = unique.get(key);
            if (existing == null || score(r) > score(existing)) {
                unique.put(key, r);
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparingDouble(this::score).reversed())
                .collect(Collectors.toList());
    }

    private double score(SearchResult r) {
        return r.score() != null ? r.score() : 0.0;
    }

    /** Cheap content fingerprint for dedup (normalized prefix). */
    private String fingerprint(String content) {
        if (content == null) return "";
        String normalized = content.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.length() > 160 ? normalized.substring(0, 160) : normalized;
    }
}