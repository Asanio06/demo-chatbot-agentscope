package io.agentscope.demo.back.config;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.demo.back.metar.MetarTafDecodeTool;
import io.agentscope.extensions.model.ollama.OllamaChatModel;
import io.agentscope.extensions.model.ollama.options.OllamaOptions;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import io.agentscope.spring.boot.agui.common.AguiAgentId;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the agentscope-java v2 METAR/TAF decoding agent.
 *
 * <p>Builds a {@link ReActAgent} named {@code metar-taf-decoder} running on a <b>local
 * Ollama</b> model (default {@code qwen3:8b}) served by the Ollama HTTP API, and which
 * persists its conversational state through {@link PostgresAgentStateStore}.</p>
 *
 * <p>Ollama is fully local: <b>no API key is required</b>. The endpoint
 * ({@code baseUrl}, default {@code http://localhost:11434}) and model name are
 * configurable via environment variables so the same image works in dev and inside
 * docker-compose (where Ollama is reached through {@code host.docker.internal}).</p>
 *
 * <p>The agent is automatically registered into the AG-UI agent registry by
 * {@code io.agentscope.spring.boot.agui.common.AguiAgentAutoRegistration}, which scans
 * for {@code Agent} beans. The runtime is then exposed under {@code /api/copilotkit}
 * (see {@code agentscope.agui.path-prefix}).</p>
 */
@Configuration
public class AgentscopeAgentConfig {

    /** Well-known agent id used by the AG-UI runtime and CopilotKit frontend. */
    public static final String AGENT_ID = "metar-taf-decoder";

    @Bean
    public Model metarTafModel(
            @Value("${agentscope.model.name:qwen3:8b}") String modelName,
            @Value("${OLLAMA_BASE_URL:http://localhost:11434}") String baseUrl,
            @Value("${OLLAMA_NUM_CTX:16384}") int numCtx,
            @Value("${OLLAMA_TEMPERATURE:0.3}") double temperature) {
        return OllamaChatModel.builder()
            .modelName(modelName)
            .baseUrl(baseUrl)
            .stream(true)
            .defaultOptions(OllamaOptions.builder()
                .numCtx(numCtx)
                .temperature(temperature)
                .build())
            .build();
    }

    @Bean
    public PostgresAgentStateStore postgresAgentStateStore(DataSource dataSource) {
        return PostgresAgentStateStore.builder(dataSource)
            .schemaName("agentscope")
            .tableName("agent_state")
            .createIfNotExist(true)
            .build();
    }

    /**
     * Registers the tested {@link MetarTafDecodeTool} (backed by {@code MetarTafDecoder})
     * as an invocable tool of the agent. The ReAct loop may then call
     * {@code decode_metar_taf} so METAR/TAF decoding runs through the deterministic Java
     * rules (covered by {@code MetarTafDecoderTest}) instead of purely by the LLM.
     */
    @Bean
    public Toolkit metarTafToolkit(MetarTafDecodeTool decodeTool) {
        Toolkit toolkit = new Toolkit();
        toolkit.registration().tool(decodeTool).apply();
        return toolkit;
    }

    @Bean
    @AguiAgentId(AGENT_ID)
    public ReActAgent metarTafAgent(
            Model metarTafModel,
            PostgresAgentStateStore postgresAgentStateStore,
            Toolkit metarTafToolkit) {
        return ReActAgent.builder()
            .name(AGENT_ID)
            .description("Agent conversationnel qui décode les bulletins météo aviation METAR et TAF.")
            .toolkit(metarTafToolkit)
            .sysPrompt("""
                Tu es un assistant météo aviation spécialisé dans le décodage des bulletins \
                METAR et TAF bruts.

                IMPORTANT : quand un utilisateur te colle un bulletin METAR ou TAF brut, \
                tu DOIS utiliser l'outil « decode_metar_taf » avec le bulletin comme \
                argument « bulletin ». C'est cet outil qui produit le décodage structuré \
                (règles Java déterministes). Restitue ensuite ce décodage à l'utilisateur \
                tel quel, proprement.

                Si l'outil répond « Bulletin non reconnu » ou que le texte fourni ne \
                ressemble pas à un bulletin METAR ou TAF, réponds clairement \
                « Bulletin non reconnu » sans inventer de données.""")
            .model(metarTafModel)
            .stateStore(postgresAgentStateStore)
            .build();
    }
}
