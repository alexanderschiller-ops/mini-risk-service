package de.demo.tdl.repository;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InputDbConfigTest {

    @Test
    void defaultConfigurationMatchesCompose() {
        InputDbConfig config = InputDbConfig.fromEnvironment(Map.of());
        assertEquals("inputdb", config.databaseName());
        assertEquals("postgresql://localhost:5432", config.namespace());
        assertEquals("inputdb.public.forward_rates", new InputDb(config).qualifiedTableName("forward_rates"));
    }

    @Test
    void customConfigurationDoesNotExposeCredentialsOrConnectionOptions() {
        InputDbConfig config = InputDbConfig.fromEnvironment(Map.of(
                "INPUT_DB_URL", "jdbc:postgresql://db.example:5433/risk?sslmode=require&password=query-secret",
                "INPUT_DB_USER", "private-user", "INPUT_DB_PASSWORD", "private-password"));
        assertEquals("risk", config.databaseName());
        assertEquals("postgresql://db.example:5433", config.namespace());
        assertEquals("InputDbConfig[namespace=postgresql://db.example:5433, database=risk]", config.toString());
    }

    @Test
    void omittedPortUsesPostgresqlDefault() {
        InputDbConfig config = new InputDbConfig("jdbc:postgresql://db.example/risk", "user", "password");
        assertEquals("postgresql://db.example:5432", config.namespace());
    }

    @Test
    void rejectsUnsupportedOrIncompleteDatabaseUrlsWithoutEchoingThem() {
        for (String url : new String[]{"jdbc:h2:mem:test", "jdbc:postgresql://localhost/",
                "jdbc:postgresql://private-user:secret@localhost/inputdb",
                "jdbc:postgresql://localhost:0/inputdb", "jdbc:postgresql://localhost:65536/inputdb",
                "jdbc:postgresql://localhost/my database?password=secret"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new InputDbConfig(url, "user", "password"));
            assertFalse(error.getMessage().contains("secret"));
        }
    }
}
