package rag_chatbot_application.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import rag_chatbot_application.model.RagAnswer;
import rag_chatbot_application.model.RagQueryRequest;
import rag_chatbot_application.service.RagService;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/rag")
@Tag(name = "RAG", description = "Ask grounded questions over ingested documents")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @Operation(summary = "Ask a question (non-streaming)",
            description = "Retrieves relevant context, generates a grounded answer, and returns citations.")
    @PostMapping(value = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RagAnswer ask(@Valid @RequestBody RagQueryRequest request) {
        return ragService.answer(request.question());
    }

    @Operation(summary = "Ask a question (streaming SSE)",
            description = "Same as /ask but streams tokens as they are generated.")
    @PostMapping(value = "/ask/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStream(@Valid @RequestBody RagQueryRequest request) {
        return ragService.streamAnswer(request.question());
    }
}