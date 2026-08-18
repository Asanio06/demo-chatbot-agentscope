package io.agentscope.demo.back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo METAR/TAF chatbot backend.
 *
 * <p>Spring Boot 4 application that integrates agentscope-java v2: a ReActAgent
 * conversational agent decodes raw METAR/TAF aviation weather bulletins, state is
 * persisted via {@code PostgresAgentStateStore}, and the runtime is exposed through
 * the AG-UI protocol under {@code /api/copilotkit}.</p>
 */
@SpringBootApplication
public class ChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbotApplication.class, args);
    }
}
