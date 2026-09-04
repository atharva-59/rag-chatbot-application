package rag_chatbot_application.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import rag_chatbot_application.model.IngestResponse;
//import rag_chatbot_application.model.UrlIngestRequest;
import rag_chatbot_application.model.UrlIngestRequest;
import rag_chatbot_application.service.IngestionService;

@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /** Phase 3: upload a PDF (multipart, field name "file"). */
    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestResponse ingestPdf(@RequestParam("file") MultipartFile file) {
        return ingestionService.ingestPdf(file);
    }

    /** Phase 4: ingest a web page by URL. */
    @PostMapping(value = "/url", consumes = MediaType.APPLICATION_JSON_VALUE)
    public IngestResponse ingestUrl(@Valid @RequestBody UrlIngestRequest request) {
        return ingestionService.ingestUrl(request.url());
    }
}