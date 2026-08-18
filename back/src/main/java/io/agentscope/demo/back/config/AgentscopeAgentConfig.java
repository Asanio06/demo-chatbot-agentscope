package io.agentscope.demo.back.config;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import io.agentscope.spring.boot.agui.common.AguiAgentId;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the agentscope-java v2 METAR/TAF decoding agent.
 *
 * <p>Builds a {@link ReActAgent} named {@code metar-taf-decoder} whose model provider
 * (DashScope by default) is configurable via environment variables, and which persists
 * its conversational state through {@link PostgresAgentStateStore}.</p>
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
            @Value("${agentscope.model.name:qwen-plus}") String modelName,
            @Value("${DASHSCOPE_API_KEY:}") String apiKey) {
        return DashScopeChatModel.builder()
            .modelName(modelName)
            .apiKey(apiKey)
            .stream(true)
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

    @Bean
    @AguiAgentId(AGENT_ID)
    public ReActAgent metarTafAgent(Model metarTafModel, PostgresAgentStateStore postgresAgentStateStore) {
        return ReActAgent.builder()
            .name(AGENT_ID)
            .description("Agent conversationnel qui décode les bulletins météo aviation METAR et TAF.")
            .sysPrompt("""
                Tu es un assistant météo aviation spécialisé dans le décodage des bulletins \
                METAR et TAF bruts.

                Quand un utilisateur te colle un METAR ou un TAF brut, restitue un décodage \
                structuré et lisible, en français, avec les informations suivantes si elles \
                sont présentes :
                - Aéroport (code OACI, ex. LFPG)
                - Heure (ex. jour 18 à 15:00 UTC)
                - Vent (direction ° / vitesse kt, + rafales si G..)
                - Visibilité (ex. 9999 = 10 km+, CAVOK)
                - Nuages (FEW/SCT/BKN/OVC + altitude en centaines de pieds)
                - Température / point de rosée (ex. 24/15)
                - Pression QNH (ex. Q1015 = 1015 hPa)
                - Temps présent (-RA, TS, SN, FG...) si présent
                - Tendance (NOSIG, BECMG, TEMPO...) si présente

                Conserve uniquement les termes aéronautiques standard (METAR, TAF, OACI, \
                QNH, CAVOK...) en anglais ; le reste en français.

                Si le texte fourni ne ressemble pas à un bulletin METAR ou TAF, réponds \
                clairement « Bulletin non reconnu » sans inventer de données.""")
            .model(metarTafModel)
            .stateStore(postgresAgentStateStore)
            .build();
    }
}
