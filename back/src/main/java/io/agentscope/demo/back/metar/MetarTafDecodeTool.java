package io.agentscope.demo.back.metar;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Exposes the deterministic {@link MetarTafDecoder} as an invocable agentscope-java
 * v2 tool so the {@code ReActAgent} decodes METAR/TAF bulletins through the tested
 * Java rules instead of relying only on the LLM system prompt.
 *
 * <p>The tool is registered into the agent's {@code Toolkit} via
 * {@code Toolkit.registration().tool(this)} in {@code AgentscopeAgentConfig}. The
 * {@code @Tool} / {@code @ToolParam} annotations drive the JSON schema sent to the
 * model, and the ReAct loop invokes {@link #decode(String)} with the model's
 * arguments.</p>
 */
@Component
public class MetarTafDecodeTool {

    private final MetarTafDecoder decoder = new MetarTafDecoder();

    /**
     * Decodes a raw METAR/TAF bulletin using the tested Java rules.
     *
     * @param bulletin the raw METAR or TAF bulletin (ex. {@code LFPG 181500Z 24012KT ...})
     * @return a human-readable structured decoding, or a clear "non recognised" message
     *         when the input cannot be decoded
     */
    @Tool(
        name = "decode_metar_taf",
        description = "Décode un bulletin météo aviation METAR ou TAF brut en un résumé "
            + "structuré (aéroport, heure, vent, visibilité, nuages, température/point de "
            + "rosée, QNH, temps présent, tendance). À utiliser à chaque fois qu'un "
            + "utilisateur colle un METAR ou un TAF.",
        readOnly = true,
        concurrencySafe = true
    )
    public String decode(
            @ToolParam(name = "bulletin", required = true,
                       description = "Le bulletin METAR ou TAF brut à décoder") String bulletin) {
        try {
            MetarDecoding decoding = decoder.decode(bulletin);
            return String.join("\n", decoding.summaryLines());
        } catch (IllegalArgumentException e) {
            return "Bulletin non reconnu : " + e.getMessage();
        }
    }
}
