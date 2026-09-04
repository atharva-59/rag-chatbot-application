package rag_chatbot_application.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import rag_chatbot_application.model.ChatRequest;
import rag_chatbot_application.model.ChatResponseDto;
import rag_chatbot_application.service.ChatService;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** Non-streaming: returns the full reply as JSON. */
    @PostMapping("/simple")
    public ChatResponseDto simple(@Valid @RequestBody ChatRequest request) {
        String reply = chatService.chat(request.prompt());
        return new ChatResponseDto(reply);
    }

    /** Streaming: emits tokens progressively via Server-Sent Events. */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@Valid @RequestBody ChatRequest request) {
        return chatService.streamChat(request.prompt());
    }
}