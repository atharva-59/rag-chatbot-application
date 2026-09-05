package rag_chatbot_application.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ragChatbotOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RAG Chatbot API")
                        .description("""
                                Enterprise Retrieval-Augmented Generation chatbot.
                                Ingest PDFs and web pages, then ask grounded questions
                                with citations. Features adaptive retrieval (query fan-out),
                                a resilient model fallback chain, and dual RAG modes.
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("Your Name").url("https://github.com/yourhandle"))
                        .license(new License().name("MIT")));
    }
}
