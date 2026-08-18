package io.agentscope.demo.back.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.State;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for {@link PostgresAgentStateStore} backed by a real PostgreSQL
 * running inside a Testcontainer.
 *
 * <p>Validates that the agentscope-java v2 PostgreSQL state store (the component wired
 * into the chatbot agent) can persist, reload and delete conversation {@link AgentState}.</p>
 */
@Testcontainers
class PostgresAgentStateStoreIT {

    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agentscope")
            .withUsername("test")
            .withPassword("test");

    static PostgresAgentStateStore store;

    @BeforeAll
    static void setUp() {
        postgres.start();
        DataSource dataSource = new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        store = PostgresAgentStateStore.builder(dataSource)
            .schemaName("agentscope")
            .tableName("agent_state")
            .createIfNotExist(true)
            .build();
    }

    @AfterAll
    static void tearDown() {
        if (store != null) {
            store.close();
        }
        postgres.stop();
    }

    @Test
    void savesAndReloadsAgentState() {
        // Given a state with an id
        AgentState state = AgentState.builder()
            .sessionId("it-session-1")
            .userId("agent-dev-back")
            .summary("Décodage METAR en cours")
            .build();

        // When persisted
        store.save("it-agent", "it-session-1", "user-x", state);

        // Then it can be reloaded with its summary preserved
        Optional<AgentState> reloaded = store.get("it-agent", "it-session-1", "user-x", AgentState.class);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getSummary()).isEqualTo("Décodage METAR en cours");
    }

    @Test
    void reportsExistenceOfPersistedState() {
        AgentState state = AgentState.builder()
            .sessionId("it-session-2")
            .userId("agent-dev-back")
            .build();
        store.save("it-agent", "it-session-2", "user-x", state);

        assertThat(store.exists("it-agent", "it-session-2")).isTrue();
        assertThat(store.exists("it-agent", "missing-session")).isFalse();
    }

    @Test
    void deletesState() {
        AgentState state = AgentState.builder()
            .sessionId("it-session-3")
            .userId("agent-dev-back")
            .build();
        store.save("it-agent", "it-session-3", "user-x", state);
        assertThat(store.exists("it-agent", "it-session-3")).isTrue();

        store.delete("it-agent", "it-session-3");

        assertThat(store.exists("it-agent", "it-session-3")).isFalse();
    }

    @Test
    void listsSessionIds() {
        store.save("it-agent", "it-list-1", "user-x",
            AgentState.builder().sessionId("it-list-1").userId("agent-dev-back").build());
        store.save("it-agent", "it-list-2", "user-x",
            AgentState.builder().sessionId("it-list-2").userId("agent-dev-back").build());

        assertThat(store.listSessionIds("it-agent")).contains("it-list-1", "it-list-2");
    }

    @Test
    void doesNotInventStateForUnknownSession() {
        Optional<State> missing = store.get("it-agent", "nope", "user-x", State.class);
        assertThat(missing).isEmpty();
    }
}
