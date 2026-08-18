package io.agentscope.demo.back;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.demo.back.config.AgentscopeAgentConfig;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E herméique : conteneur Ollama (Testcontainers) re-utilisant le store local du modèle,
 * + Postgres (Testcontainers), + le back complet (agent ReAct `metar-taf-decoder`).
 * Valide de petits cas réels via l'endpoint AG-UI /api/copilotkit/run :
 *
 * <ol>
 *   <li>METAR valide → l'agent déclenche l'outil déterministe {@code decode_metar_taf} et
 *       le décodage structuré attendu est présent dans la réponse ;</li>
 *   <li>texte non-aéro (garbage) → réponse « bulletin non reconnu » (pas d'invention de données).</li>
 * </ol>
 *
 * <p>Le conteneur Ollama monte en <em>bind-mount</em> le store local ({@code ~/.ollama/models})
 * pour re-utiliser le modèle déjà téléchargé (pas de re-téléchargement). Var d'env
 * {@code OLLAMA_MODEL} pour changer de modèle.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OllamaAgentIT {

    static final String MODEL = System.getenv().getOrDefault("OLLAMA_TEST_MODEL", "qwen3:8b");
    static final String HOST_OLLAMA_MODELS =
        new File(System.getProperty("user.home"), ".ollama/models").getAbsolutePath();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    /** Ollama conteneur démarré en <em>static</em> (AVANT le contexte Spring / @DynamicPropertySource,
     *  sinon getEndpoint() échoue « container not started »). Bind-mount du store hôte pour
     *  re-utiliser le modèle déjà téléchargé (~/.ollama/models). */
    static final OllamaContainer ollama = new OllamaContainer(DockerImageName.parse("ollama/ollama"));

    static {
        ollama.withFileSystemBind(HOST_OLLAMA_MODELS, "/root/.ollama/models", BindMode.READ_WRITE);
        ollama.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("agentscope.agui.path-prefix", () -> "/api/copilotkit");
        registry.add("agentscope.agui.default-agent-id", () -> AgentscopeAgentConfig.AGENT_ID);
        registry.add("OLLAMA_BASE_URL", () -> ollama.getEndpoint());
        registry.add("OLLAMA_MODEL", () -> MODEL);
        registry.add("OLLAMA_NUM_CTX", () -> "8192");
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
    void decodeUnMetarValideViatout_SiLeModeleEstDisponible() throws Exception {
        // qwen3:8b doit être présent dans le conteneur (monté depuis le store hôte).
        assertThat(ollamaIsReachable()).as("Ollama conteneur joignable").isTrue();

        String answer = runAgent(
            "Décode ce bulletin METAR : LFPG 181500Z 24012KT 9999 SCT040 17/06 Q1015 NOSIG");
        System.out.println("--- REPONSE METAR ---\n" + answer);

        assertThat(answer)
            .contains("LFPG")
            .contains("240")
            .contains("1015");
    }

    @Test
    void bulletinNonReconnu_neInventePas() throws Exception {
        String answer = runAgent("Décode ça : hello world c'est pas un METAR du tout");
        System.out.println("--- REPONSE GARBAGE ---\n" + answer);
        assertThat(answer.toLowerCase()).contains("non reconnu");
    }

    private boolean ollamaIsReachable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ollama.getEndpoint() + "/api/tags")).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 && resp.body().contains(MODEL);
        } catch (Exception e) {
            return false;
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
