package com.example.db;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

@Configuration
@EnableConfigurationProperties(DatabaseProperties.class)
@ConditionalOnProperty(prefix = "app.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    public DataSource dataSource(DatabaseProperties properties) {
        createDatabaseIfNotExists(properties);
        ensureApplicationSchemaCompatibility(properties);

        HikariDataSource dataSource = new HikariDataSource();

        dataSource.setJdbcUrl(targetDatabaseUrl(properties));
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");

        return dataSource;
    }

    private void ensureApplicationSchemaCompatibility(DatabaseProperties properties) {
        try (var connection = DriverManager.getConnection(
                targetDatabaseUrl(properties),
                properties.getUsername(),
                properties.getPassword()
        )) {
            connection.setAutoCommit(true);

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS app_users ("
                                + "username text PRIMARY KEY, "
                                + "password_hash text NOT NULL, "
                                + "salt text NOT NULL, "
                                + "role text NOT NULL, "
                                + "created_at timestamp NOT NULL DEFAULT now()"
                                + ")"
                );
                statement.executeUpdate(
                        "ALTER TABLE app_users "
                                + "ADD COLUMN IF NOT EXISTS password_hash text NOT NULL DEFAULT 'legacy-unusable'"
                );
                statement.executeUpdate(
                        "ALTER TABLE app_users "
                                + "ADD COLUMN IF NOT EXISTS salt text NOT NULL DEFAULT 'legacy-unusable'"
                );
                statement.executeUpdate(
                        "ALTER TABLE app_users "
                                + "ADD COLUMN IF NOT EXISTS role text NOT NULL DEFAULT 'USER'"
                );
                statement.executeUpdate(
                        "ALTER TABLE app_users "
                                + "ADD COLUMN IF NOT EXISTS created_at timestamp NOT NULL DEFAULT now()"
                );

                if (tableExists(connection, "inspections")) {
                    statement.executeUpdate(
                            "ALTER TABLE inspections "
                                    + "ADD COLUMN IF NOT EXISTS critical_violations_count integer NOT NULL DEFAULT 0"
                    );
                }

                if (tableExists(connection, "contractors")) {
                    statement.executeUpdate(
                            "ALTER TABLE contractors "
                                    + "ADD COLUMN IF NOT EXISTS critical_violations_count integer NOT NULL DEFAULT 0"
                    );
                    statement.executeUpdate(
                            "ALTER TABLE contractors "
                                    + "ADD COLUMN IF NOT EXISTS total_score integer NOT NULL DEFAULT 0"
                    );
                    statement.executeUpdate(
                            "ALTER TABLE contractors "
                                    + "ADD COLUMN IF NOT EXISTS inspections_count integer NOT NULL DEFAULT 0"
                    );
                    statement.executeUpdate(
                            "ALTER TABLE contractors "
                                    + "ADD COLUMN IF NOT EXISTS rating double precision NOT NULL DEFAULT 0"
                    );
                    statement.executeUpdate(
                            "UPDATE contractors "
                                    + "SET total_score = violations_count + critical_violations_count * 5 "
                                    + "WHERE total_score = 0 AND violations_count > 0"
                    );
                    statement.executeUpdate(
                            "UPDATE contractors "
                                    + "SET inspections_count = 1 "
                                    + "WHERE inspections_count = 0 AND total_score > 0"
                    );
                    statement.executeUpdate(
                            "UPDATE contractors "
                                    + "SET rating = CASE "
                                    + "WHEN inspections_count > 0 THEN total_score::double precision / inspections_count "
                                    + "ELSE 0 END"
                    );
                    statement.executeUpdate(
                            "CREATE INDEX IF NOT EXISTS idx_contractors_total_score_asc "
                                    + "ON contractors (total_score ASC)"
                    );
                    statement.executeUpdate(
                            "CREATE INDEX IF NOT EXISTS idx_contractors_rating_asc "
                                    + "ON contractors (rating ASC)"
                    );
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Cannot update PostgreSQL schema compatibility for database '%s' at %s:%d."
                            .formatted(properties.getName(), properties.getHost(), properties.getPort()),
                    e
            );
        }
    }

    private boolean tableExists(java.sql.Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?"
        )) {
            statement.setString(1, tableName);

            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void createDatabaseIfNotExists(DatabaseProperties properties) {
        String adminUrl = adminDatabaseUrl(properties);

        try (var connection = DriverManager.getConnection(
                adminUrl,
                properties.getUsername(),
                properties.getPassword()
        )) {
            connection.setAutoCommit(true);

            if (!databaseExists(connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?"), properties.getName())) {
                log.warn("Database '{}' does not exist. Creating it...", properties.getName());

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE DATABASE " + safeDatabaseName(properties.getName()));
                }

                log.info("Database '{}' was successfully created", properties.getName());
            } else {
                log.info("Database '{}' already exists", properties.getName());
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    (
                            "Cannot connect to PostgreSQL admin database '%s' at %s:%d as user '%s'. "
                                    + "Check APICOLLECTOR_DB_USERNAME and APICOLLECTOR_DB_PASSWORD, or update app.database.* in application.yaml."
                    ).formatted(
                            properties.getAdminDatabase(),
                            properties.getHost(),
                            properties.getPort(),
                            properties.getUsername()
                    ),
                    e
            );
        }
    }

    private boolean databaseExists(PreparedStatement statement, String databaseName) throws SQLException {
        statement.setString(1, databaseName);

        try (var resultSet = statement.executeQuery()) {
            return resultSet.next();
        }
    }

    private String adminDatabaseUrl(DatabaseProperties properties) {
        return "jdbc:postgresql://"
                + properties.getHost()
                + ":"
                + properties.getPort()
                + "/"
                + properties.getAdminDatabase();
    }

    private String targetDatabaseUrl(DatabaseProperties properties) {
        return "jdbc:postgresql://"
                + properties.getHost()
                + ":"
                + properties.getPort()
                + "/"
                + properties.getName();
    }

    private String safeDatabaseName(String databaseName) {
        if (!databaseName.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Unsafe database name: " + databaseName);
        }

        return databaseName;
    }
}
