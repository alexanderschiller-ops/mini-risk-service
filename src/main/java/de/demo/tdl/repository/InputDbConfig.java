package de.demo.tdl.repository;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** PostgreSQL connection settings. Defaults match the local Compose demo. */
public final class InputDbConfig {

    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final String databaseName;
    private final String namespace;

    public InputDbConfig(String jdbcUrl, String user, String password) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.user = Objects.requireNonNull(user, "user");
        this.password = Objects.requireNonNull(password, "password");
        URI uri;
        try {
            if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
                throw new IllegalArgumentException("INPUT_DB_URL muss eine PostgreSQL-JDBC-URL sein");
            }
            uri = new URI(jdbcUrl.substring("jdbc:".length()));
        } catch (URISyntaxException ex) {
            // Do not echo a URL that might contain credentials in its query string.
            throw new IllegalArgumentException("INPUT_DB_URL ist keine gültige PostgreSQL-JDBC-URL");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getPath() == null || !uri.getPath().matches("/[^/]+")
                || uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException(
                    "INPUT_DB_URL muss jdbc:postgresql://host:port/datenbank entsprechen; "
                            + "Benutzer und Passwort über INPUT_DB_USER/INPUT_DB_PASSWORD setzen");
        }
        this.databaseName = uri.getPath().substring(1);
        this.namespace = "postgresql://" + uri.getHost() + ":" + (uri.getPort() < 0 ? 5432 : uri.getPort());
    }

    public static InputDbConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static InputDbConfig fromEnvironment(Map<String, String> environment) {
        return new InputDbConfig(
                environment.getOrDefault("INPUT_DB_URL", "jdbc:postgresql://localhost:5432/inputdb"),
                environment.getOrDefault("INPUT_DB_USER", "inputdb"),
                environment.getOrDefault("INPUT_DB_PASSWORD", "inputdb"));
    }

    public String databaseName() {
        return databaseName;
    }

    public String namespace() {
        return namespace;
    }

    Connection openConnection() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("socketTimeout", "60");
        return DriverManager.getConnection(jdbcUrl, properties);
    }

    @Override
    public String toString() {
        return "InputDbConfig[namespace=" + namespace + ", database=" + databaseName + "]";
    }
}
