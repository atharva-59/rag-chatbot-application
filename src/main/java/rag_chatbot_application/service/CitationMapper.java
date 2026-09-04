package rag_chatbot_application.service;



import rag_chatbot_application.model.Citation;
import rag_chatbot_application.model.SearchResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Converts retrieved chunks into clean, user-facing citations.
 * Deduplicates by source, keeping the best-scoring snippet per source.
 */
public final class CitationMapper {

    private CitationMapper() {}

    public static List<Citation> from(List<SearchResult> hits) {
        Map<String, Citation> bySource = new LinkedHashMap<>();

        for (SearchResult r : hits) {
            Map<String, Object> meta = r.metadata() != null ? r.metadata() : Map.of();
            String source = String.valueOf(meta.getOrDefault("source", "unknown"));
            String title = meta.get("title") != null ? String.valueOf(meta.get("title")) : null;
            double score = r.score() != null ? r.score() : 0.0;

            Citation existing = bySource.get(source);
            if (existing == null || score > (existing.score() != null ? existing.score() : 0.0)) {
                bySource.put(source, new Citation(source, title, snippet(r.content()), score));
            }
        }

        return bySource.values().stream()
                .sorted(Comparator.comparingDouble(
                        (Citation c) -> c.score() != null ? c.score() : 0.0).reversed())
                .collect(Collectors.toList());
    }

    private static String snippet(String content) {
        if (content == null) return "";
        String s = content.trim().replaceAll("\\s+", " ");
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
