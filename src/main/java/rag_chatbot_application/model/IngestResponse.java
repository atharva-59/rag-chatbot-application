package rag_chatbot_application.model;

/**
 * Response returned after ingesting a source (PDF/URL) into the vector store.
 *
 * @param source       the source identifier (e.g. original filename)
 * @param pagesRead    number of pages/documents extracted before splitting
 * @param chunksStored number of chunks embedded and stored
 */
public record IngestResponse(
        String source,
        int pagesRead,
        int chunksStored
) {
}
