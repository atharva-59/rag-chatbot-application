package rag_chatbot_application.service;


import rag_chatbot_application.model.SearchResult;

import java.util.List;

/**
 * Adaptive retrieval: classifies the query, applies fan-out for broad queries,
 * and returns deduplicated, ranked results.
 */
public interface RetrievalService {

    /** Retrieves relevant, deduplicated chunks for the given question. */
    List<SearchResult> retrieve(String question);
}