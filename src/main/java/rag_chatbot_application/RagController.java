package rag_chatbot_application;

//import com.example.ragchatbot.model.RagAnswer;
//import com.example.ragchatbot.model.RagQueryRequest;
//import com.example.ragchatbot.service.RagService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import rag_chatbot_application.model.RagAnswer;
import rag_chatbot_application.model.RagQueryRequest;
import rag_chatbot_application.service.RagService;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /** Non-streaming RAG answer with citations. */
    @PostMapping(value = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RagAnswer ask(@Valid @RequestBody RagQueryRequest request) {
        return ragService.answer(request.question());
    }

    /** Streaming RAG answer (tokens via SSE). */
    @PostMapping(value = "/ask/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStream(@Valid @RequestBody RagQueryRequest request) {
        return ragService.streamAnswer(request.question());
    }
}