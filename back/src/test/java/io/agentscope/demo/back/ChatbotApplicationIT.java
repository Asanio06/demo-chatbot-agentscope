package io.agentscope.demo.back;

import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.core.agui.registry.AguiAgentRegistry;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.demo.back.config.AgentscopeAgentConfig;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import io.agentscope.spring.boot.agui.common.AguiProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context integration test: boots the full Spring Boot application with agentscope-java v2
 * and a real PostgreSQL (Testcontainer), and asserts that
 * <ul>
 *   <li>the agentscope AG-UI runtime is exposed under {@code /api/copilotkit} (path-prefix),</li>
 *   <li>the {@code metar-taf-decoder} ReActAgent is registered in the AG-UI agent registry,</li>
 *   <li>the {@code PostgresAgentStateStore} bean is wired as the agent state store.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest
class ChatbotApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("agentscope.agui.path-prefix", () -> "/api/copilotkit");
        registry.add("agentscope.agui.default-agent-id", () -> AgentscopeAgentConfig.AGENT_ID);
        // Ollama is local (no API key). The context test only validates wiring — no model
        // call is performed — so the default OLLAMA_BASE_URL (localhost:11434) is never hit.
    }

    @Autowired
    AguiProperties aguiProperties;

    @Autowired
    AgentStateStore agentStateStore;

    @Autowired
    AguiAgentRegistry aguiAgentRegistry;

    @Autowired
    RequestMappingHandlerMapping handlerMapping;

    @Autowired
    MappingJackson2HttpMessageConverter aguiJackson2Converter;

    @Test
    void metarTafAgentIsRegisteredInAguiRegistry() {
        assertThat(aguiAgentRegistry).isNotNull();
        assertThat(aguiAgentRegistry.hasAgent(AgentscopeAgentConfig.AGENT_ID)).isTrue();
    }

    @Test
    void aguiRuntimeIsMappedUnderApiCopilotkit() {
        assertThat(aguiProperties.getPathPrefix()).isEqualTo("/api/copilotkit");
    }

    @Test
    void aguiRuntimePostEndpointIsExposedUnderApiCopilotkit() {
        // The AG-UI runtime (SSE) must be reachable under the CopilotKit v2 runtime URL.
        // With path-prefix=/api/copilotkit the controller exposes /api/copilotkit/run{...}.
        var handlers = handlerMapping.getHandlerMethods().keySet();
        assertThat(handlers)
            .anyMatch(info -> info.toString().contains("/api/copilotkit/run"));
    }

    @Test
    void postgresAgentStateStoreIsWired() {
        assertThat(agentStateStore).isInstanceOf(PostgresAgentStateStore.class);
    }

    /**
     * Régression (voir AguiJackson2ConverterConfig) : agentscope 2.0.2 est Jackson 2, Spring Boot 4
     * Jackson 3 → sans le pont, un {@code RunAgentInput} AG-UI avec un message portant du
     * {@code content} échouait en désérialisation (500 « no Creators for MessageContent »).
     * Ce test désérialise un vrai payload AG-UI via le converter Jackson 2 enregistré par le pont
     * et vérifie qu'il produit bien un {@link RunAgentInput} (au lieu de lever une erreur Jackson 3).
     */
    @Test
    void aguiRunInputWithMessageContentDeserializes() throws Exception {
        assertThat(aguiJackson2Converter).isNotNull();
        assertThat(aguiJackson2Converter.canRead(RunAgentInput.class, MediaType.APPLICATION_JSON))
            .as("le pont doit capter les types AG-UI via Jackson 2")
            .isTrue();

        String json = """
            {
              "threadId": "it-thread",
              "runId": "it-run-1",
              "agentId": "%s",
              "messages": [
                { "id": "m1", "role": "user", "content": "Décode ce METAR : LFPG 181500Z 24012KT" }
              ]
            }
            """.formatted(AgentscopeAgentConfig.AGENT_ID);

        MockHttpInputMessage input = new MockHttpInputMessage(
            json.getBytes(StandardCharsets.UTF_8));
        input.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        RunAgentInput run = (RunAgentInput) aguiJackson2Converter.read(
            RunAgentInput.class, input);
        assertThat(run.getThreadId()).isEqualTo("it-thread");
        assertThat(run.getRunId()).isEqualTo("it-run-1");
        assertThat(run.hasMessages()).isTrue();
        assertThat(run.getMessages()).hasSize(1);
    }
}
