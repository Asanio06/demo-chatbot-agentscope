# demo-chatbot-agentscope

Mini **chatbot aéronautique METAR/TAF** — démo full-stack :

- **Back** : Spring Boot 4 (Java 25, Maven) + **agentscope-java v2**, AG-UI exposé sous `/api/copilotkit`, persistance d'état via `PostgresAgentStateStore`.
- **Front** : React 19 + Ant Design 6 + **CopilotKit v2** (`@copilotkit/react-core/v2`, `runtimeUrl="/api/copilotkit"`).
- **Modèle LLM** : **Ollama local** (défaut `qwen3:8b`), aucun API key cloud requis.

L'agent `metar-taf-decoder` est un `ReActAgent` qui, face à un bulletin brut, invoque l'outil
déterministe **`decode_metar_taf`** (règles Java testées) au lieu de laisser l'LLM décoder seul.

## Stack / architecture

| Brique | Détail |
|---|---|
| Back | `back/` — Spring Boot 4, `spring-boot-starter-web`, agentscope-java v2.0.2 AG-UI |
| Front | `front/` — React 19 + Vite + AntD 6 + CopilotKit v2 |
| Modèle | Ollama local (`qwen3:8b`, configurable via `OLLAMA_MODEL`) |
| Persistance | Postgres 16 (via `PostgresAgentStateStore`, volume docker) |
| Orchestration | `docker-compose.yml` — `postgres` + `back` + `front` |

## TL;DR — lancer avec Docker

Prérequis : Docker Desktop + une instance **Ollama** qui tourne sur l'hôte avec le modèle pullé.

```bash
# 1. (une fois) télécharger le modèle local
ollama pull qwen3:8b

# 2. démarrer toute la stack
docker compose up --build

# → front sur http://localhost:5173
# → back  sur http://localhost:8080  (AG-UI /api/copilotkit)
```

Le conteneur `back` joint l'Ollama de l'hôte via `host.docker.internal:11434`
(ajustable par `OLLAMA_BASE_URL`, modèle par `OLLAMA_MODEL`).

> Pour un modèle non-Ollama (ex. OpenAI) : remplacer le provider dans
> `back/pom.xml` + `application.yml` + `AgentscopeAgentConfig` (cf. §LLM local ci-dessous).

## LLM local : Ollama sur RTX 5070 (12 Go)

Le choix est **`qwen3:8b`** (~5,2 Go) : meilleur équilibre **tool-calling + raisonnement** pour
12 Go de VRAM — le projet dépend d'un outil déterministe, donc la fiabilité du function-calling
prime sur la seule puissance de raisonnement. (`deepseek-r1:8b` raisonne mieux mais gère mal
les tools ; `qwen3:14b` tient mais avec un contexte plus serré.)

Config dans `back/src/main/java/io/agentscope/demo/back/config/AgentscopeAgentConfig.java` :

| Variable | Défaut | Rôle |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | endpoint Ollama |
| `OLLAMA_MODEL` (via `agentscope.model.name`) | `qwen3:8b` | modèle |
| `OLLAMA_NUM_CTX` | `16384` | taille de contexte (tokens) |
| `OLLAMA_TEMPERATURE` | `0.3` | créativité |

## Intégration Jackson 2 (important pour Spring Boot 4)

agentscope-java 2.0.2 est bâti contre **Jackson 2**, or Spring Boot 4 s'appuie par défaut sur
**Jackson 3** (`tools.jackson`) qui ne lit pas les annotations Jackson 2 — sans correctif, la
désérialisation des requêtes AG-UI échoue dès qu'un message a du `content` (500 « no Creators for
MessageContent »). Deux correctifs, vus par le vrai E2E :

1. Dépendance officielle **`spring-boot-jackson2`** (module Spring Boot 4).
2. **`AguiJackson2ConverterConfig`** : un `MappingJackson2HttpMessageConverter` Jackson 2 *sélectif*
   (restreint aux types `io.agentscope.core.agui.model`), placé en tête des converters MVC — seul le
   trafic AG-UI passe par Jackson 2, le reste de l'app garde Jackson 3.

Un test de régression couvre cette désérialisation (`ChatbotApplicationIT.aguiRunInputWithMessageContentDeserializes`).

## Tester

```bash
# Tests back (Testcontainers postgres réels) — nécessite Docker
cd back && mvn test

# Build + lint front
cd front && npm ci && npm run build && npm run lint

# E2E réel (stack lancée + Ollama) : l'agent doit déclencher decode_metar_taf et décoder un METAR
cd .. && python scripts/e2e_metar_chatbot.py http://localhost:8080
```

### Test d'intégration Ollama+Testcontainers (`OllamaAgentIT`)

Le test JUnit `OllamaAgentIT` est un **vrai E2E automatisé et portable** (CI-ready) : il lance un
conteneur **`ollama/ollama`** (Testcontainers, GPU-ready) qui **pull lui-même** un petit modèle
(défaut `qwen3:0.6b` — aucun recours au store local) + un Postgres Testcontainers + le back
complet, puis valide de **petits cas** via `/api/copilotkit/run` :

- METAR valide → l'agent déclenche `decode_metar_taf`, le décodage est relayé (code OACI) ;
- texte non-aéro → réponse « bulletin non reconnu » (rien d'inventé).

```bash
cd back && mvn test -Dtest=OllamaAgentIT
```

> Notes : nécessite Docker. `OLLAMA_TEST_MODEL` (défaut `qwen3:0.6b`) pour changer de modèle —
> ex. `qwen3:8b` pour un tool-calling plus fiable (le petit modèle portable peut varier sur
> l'invocation d'outil). Le pull est idempotent (ne re-télécharge pas si déjà présent).

## Branches

Livré sur **`feat/integration-docker`** (branche unique d'intégration poussée sur
`github.com/Asanio06/demo-chatbot-agentscope`).
