package io.agentscope.demo.back;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.demo.back.config.AgentscopeAgentConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E herméique et <b>portable</b> : conteneur Ollama (Testcontainers) qui <b>pull lui-même</b>
 * un petit modèle (défaut {@code qwen3:0.6b}, → CI-ready sans dépendre du store de la machine),
 * + un Postgres (Testcontainers), + le back complet (agent ReAct `metar-taf-decoder`).
 *
 * <p>Valide de petits cas réels via l'endpoint AG-UI /api/copilotkit/run :</p>
 * <ol>
 *   <li>METAR valide → l'agent déclenche l'outil déterministe {@code decode_metar_taf} et
 *       renvoie un décodage (le code OACI est au minimum relayé) ;</li>
 *   <li>texte non-aéro (garbage) → réponse « bulletin non reconnu » (pas d'invention de données).</li>
 * </ol>
 *
 * <p>Le conteneur Ollama pull le modèle via l'API HTTP ({@code POST /api/pull}) — indépendant du
 * store hôte, donc rejouable en CI sur n'importe quelle machine. Modèle par défaut {@code qwen3:0.6b}
 * (léger, pull rapide) ; surcharger {@code OLLAMA_TEST_MODEL} (ex. {@code qwen3:8b}) pour un
 * tool-calling plus fiable (avec un modèle minuscule, l'invocation d'outil peut varier).</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OllamaAgentIT {

    /** Petit modèle portable (pull automatique dans le conteneur). */
    static final String MODEL = System.getenv().getOrDefault("OLLAMA_TEST_MODEL", "qwen3:0.6b");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    /** Ollama conteneur démarré en <em>static</em> AVANT le contexte Spring (sinon getEndpoint()
     *  échoue « container not started »). Le modèle est pullé dès le démarrage. */
    static final OllamaContainer ollama = new OllamaContainer(DockerImageName.parse("ollama/ollama"));

    static final HttpClient PULL_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15)).build();

    static {
        ollama.start();
        pullModelInContainer(MODEL);
    }

    /** Pull `model` dans le conteneur via POST /api/pull (streaming) — idempotent si déjà présent. */
    private static void pullModelInContainer(String model) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ollama.getEndpoint() + "/api/pull"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"" + model + "\"}"))
                .build();
            HttpResponse<String> resp = PULL_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("Échec pull Ollama " + model + " HTTP " + resp.statusCode()
                    + " -> " + resp.body());
            }
            System.out.println("Ollama: modèle " + model + " prêt dans le conteneur");
        } catch (Exception e) {
            throw new IllegalStateException("Échec pull du modèle Ollama " + model + " : " + e, e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("agentscope.agui.path-prefix", () -> "/api/copilotkit");
        registry.add("agentscope.agui.default-agent-id", () -> AgentscopeAgentConfig.AGENT_ID);
        registry.add("OLLAMA_BASE_URL", () -> ollama.getEndpoint());
        registry.add("OLLAMA_MODEL", () -> MODEL);
        registry.add("OLLAMA_NUM_CTX", () -> "4096");
        registry.add("OLLAMA_TEMPERATURE", () -> "0.2");
    }

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final AtomicInteger runSeq = new AtomicInteger();

    String runAgent(String userText) throws Exception {
        String payload = """
            {
              "threadId": "ollama-it",
              "runId": "run-%d",
              "agentId": "%s",
              "messages": [
                { "id": "m-%d", "role": "user", "content": "%s" }
              ]
            }
            """.formatted(runSeq.incrementAndGet(), AgentscopeAgentConfig.AGENT_ID,
                runSeq.get(), escapeJson(userText));

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/copilotkit/run"))
            .timeout(Duration.ofMinutes(5))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new AssertionError("AG-UI run HTTP " + resp.statusCode() + " -> " + resp.body());
        }
        String text = collectText(resp.body());
        if (text.isEmpty()) {
            System.out.println("[DIAG] réponse vide — stream brut:\n" + resp.body());
        }
        return text;
    }

    /** Reconstitue le texte de la réponse AG-UI en agrégeant les events SSE. */
    String collectText(String stream) {
        StringBuilder sb = new StringBuilder();
        for (String line : stream.split("\\R")) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) {
                continue;
            }
            JsonNode node;
            try {
                node = new ObjectMapper().readTree(data);
            } catch (Exception e) {
                continue;
            }
            sb.append(extractText(node));
        }
        return sb.toString();
    }

    /** Extrait le texte de n'importe quel event AG-UI (delta, content str ou liste de blocs). */
    private static String extractText(JsonNode node) {
        String delta = node.path("delta").asText("");
        if (!delta.isEmpty()) {
            return delta;
        }
        JsonNode content = node.path("content");
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : content) {
                String t = block.path("text").asText("");
                if (!t.isEmpty()) {
                    sb.append(t).append(' ');
                }
            }
            return sb.toString();
        }
        return "";
    }

    @Test
    void decodeUnMetarValide() throws Exception {
        String answer = runAgent(
            "Décode ce bulletin METAR : LFPG 181500Z 24012KT 9999 SCT040 17/06 Q1015 NOSIG");
        System.out.println("--- REPONSE METAR ---\n" + answer);

        // Avec le petit modèle portable, on vérifie que le flux a fonctionné et que le décodage
        // (via l'outil déterministe) restitue au moins le code OACI. La fidélité complète du
        // tool-calling dépend du modèle (voir javadoc de classe pour OLLAMA_TEST_MODEL).
        assertThat(answer)
            .as("l'agent doit répondre sur un METAR (code OACI relayé)")
            .isNotBlank()
            .contains("LFPG");
    }

    @Test
    void bulletinNonReconnu_neInventePas() throws Exception {
        String answer = runAgent("Décode ça : hello world c'est pas un METAR du tout");
        System.out.println("--- REPONSE GARBAGE ---\n" + answer);
        // Cas déterministe : l'outil renvoie « Bulletin non reconnu » et le sysPrompt force le relai.
        assertThat(answer.toLowerCase()).contains("non reconnu");
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
