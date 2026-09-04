package rag_chatbot_application.service;

import org.springframework.web.multipart.MultipartFile;
import rag_chatbot_application.model.IngestResponse;

/**
 * Service contract for ingesting documents into the knowledge base.
 */
public interface IngestionService {

    /** Extracts, chunks, embeds and stores a PDF file. */
    IngestResponse ingestPdf(MultipartFile file);

    /** Fetches a web page, extracts text, chunks, embeds and stores it. */
    IngestResponse ingestUrl(String url);
}