package collabdesk.user.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountProfileMigrationTest {

    @Test
    void backfillsExistingUsersAndAllowsGitHubIdentity() throws Exception {
        try (MySQLContainer mysql = new MySQLContainer("mysql:8.4.10")) {
            mysql.start();

            Flyway.configure()
                    .dataSource(
                            mysql.getJdbcUrl(),
                            mysql.getUsername(),
                            mysql.getPassword()
                    )
                    .target("11")
                    .load()
                    .migrate();

            long userId;
            Timestamp createdAt = Timestamp.from(
                    Instant.parse("2026-08-01T10:15:30Z")
            );
            try (Connection connection = connection(mysql);
                 PreparedStatement insert = connection.prepareStatement(
                         """
                         INSERT INTO users (
                             email,
                             display_name,
                             status,
                             created_at,
                             updated_at,
                             version
                         ) VALUES (?, ?, 'ACTIVE', ?, ?, 0)
                         """,
                         Statement.RETURN_GENERATED_KEYS
                 )) {
                insert.setString(1, "existing@example.com");
                insert.setString(2, "Existing User");
                insert.setTimestamp(3, createdAt);
                insert.setTimestamp(4, createdAt);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    keys.next();
                    userId = keys.getLong(1);
                }
            }

            Flyway.configure()
                    .dataSource(
                            mysql.getJdbcUrl(),
                            mysql.getUsername(),
                            mysql.getPassword()
                    )
                    .load()
                    .migrate();

            try (Connection connection = connection(mysql);
                 PreparedStatement select = connection.prepareStatement(
                         """
                         SELECT first_name,
                                last_name,
                                email_verified_at,
                                onboarding_completed_at
                         FROM users
                         WHERE id = ?
                         """
                 )) {
                select.setLong(1, userId);
                try (ResultSet result = select.executeQuery()) {
                    result.next();
                    assertAll(
                            () -> assertEquals(
                                    "Existing User",
                                    result.getString("first_name")
                            ),
                            () -> assertNull(result.getString("last_name")),
                            () -> assertNotNull(
                                    result.getTimestamp("email_verified_at")
                            ),
                            () -> assertNotNull(
                                    result.getTimestamp("onboarding_completed_at")
                            )
                    );
                }

                try (PreparedStatement insertGitHub = connection.prepareStatement(
                        """
                        INSERT INTO auth_identities (
                            user_id,
                            provider,
                            provider_subject,
                            password_hash,
                            created_at
                        ) VALUES (?, 'GITHUB', '12345678', NULL, ?)
                        """
                )) {
                    insertGitHub.setLong(1, userId);
                    insertGitHub.setTimestamp(2, createdAt);
                    assertEquals(1, insertGitHub.executeUpdate());
                }

                assertThrows(
                        SQLException.class,
                        () -> insertUnknownProvider(connection, userId, createdAt)
                );
            }
        }
    }

    private Connection connection(MySQLContainer mysql) throws SQLException {
        return DriverManager.getConnection(
                mysql.getJdbcUrl(),
                mysql.getUsername(),
                mysql.getPassword()
        );
    }

    private void insertUnknownProvider(
            Connection connection,
            long userId,
            Timestamp createdAt
    ) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO auth_identities (
                    user_id,
                    provider,
                    provider_subject,
                    password_hash,
                    created_at
                ) VALUES (?, 'UNKNOWN', 'unknown-subject', NULL, ?)
                """
        )) {
            insert.setLong(1, userId);
            insert.setTimestamp(2, createdAt);
            insert.executeUpdate();
        }
    }
}
