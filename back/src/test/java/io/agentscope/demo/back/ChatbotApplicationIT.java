package io.agentscope.demo.back;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.agui.registry.AguiAgentRegistry;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.demo.back.config.AgentscopeAgentConfig;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import io.agentscope.spring.boot.agui.common.AguiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
}
