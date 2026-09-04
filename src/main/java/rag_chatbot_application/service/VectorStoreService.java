package rag_chatbot_application.service;

import org.springframework.ai.document.Document;
import rag_chatbot_application.model.SearchResult;

import java.util.List;

/**
 * Service contract for embedding + vector storage/retrieval.
 */
public interface VectorStoreService {

    /** Embeds and stores raw texts as documents. Returns count stored. */
    int store(List<String> texts);

    /** Embeds and stores pre-built documents (with metadata). Returns count stored. */
    int storeDocuments(List<Document> documents);

    /** Runs a semantic similarity search and returns the top-K results. */
    List<SearchResult> search(String query, int topK, double threshold);

    /** Returns the actual embedding dimension produced by the model (for validation). */
    int embeddingDimension();
}