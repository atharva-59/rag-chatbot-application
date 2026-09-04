package rag_chatbot_application.service;

//import com.example.ragchatbot.model.RagAnswer;
import rag_chatbot_application.model.RagAnswer;
import reactor.core.publisher.Flux;

/**
 * Orchestrates the full RAG loop: retrieve -> augment -> generate.
 */
public interface RagService {

    /** Blocking RAG answer including the sources used. */
    RagAnswer answer(String question);

    /** Streaming RAG answer (tokens streamed as they are generated). */
    Flux<String> streamAnswer(String question);
}
