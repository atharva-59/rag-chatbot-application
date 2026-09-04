package rag_chatbot_application.service;

/**
 * Abstraction over fetching + extracting readable text from a URL.
 * Isolated so ingestion logic can be tested with a mock (no network).
 */
public interface WebPageFetcher {

    /** Returns extracted, cleaned page content. */
    FetchedPage fetch(String url);

    /** Simple value holder for fetched content. */
    record FetchedPage(String title, String text) {
    }
}