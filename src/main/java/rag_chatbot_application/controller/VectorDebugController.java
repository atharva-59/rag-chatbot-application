package rag_chatbot_application.controller;

//import com.example.ragchatbot.model.SearchResult;
//import com.example.ragchatbot.service.VectorStoreService;
import org.springframework.web.bind.annotation.*;
import rag_chatbot_application.model.SearchResult;
import rag_chatbot_application.service.VectorStoreService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class VectorDebugController {

    private final VectorStoreService vectorStoreService;

    public VectorDebugController(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    /** Reports the ACTUAL embedding dimension. Use this to set pgvector.dimensions. */
    @GetMapping("/embedding-dimension")
    public Map<String, Integer> dimension() {
        return Map.of("dimension", vectorStoreService.embeddingDimension());
    }

    /** Stores a batch of raw texts. Body: {"texts": ["...", "..."]} */
    @PostMapping("/store")
    public Map<String, Integer> store(@RequestBody StoreRequest request) {
        int count = vectorStoreService.store(request.texts());
        return Map.of("stored", count);
    }

    /** Runs a similarity search. */
    @GetMapping("/search")
    public List<SearchResult> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "4") int topK,
            @RequestParam(defaultValue = "0.0") double threshold) {
        return vectorStoreService.search(q, topK, threshold);
    }

    public record StoreRequest(List<String> texts) {
    }
}