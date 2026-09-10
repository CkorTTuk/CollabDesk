package collabdesk.user.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PreferredLocaleMigrationTest {
    @Test
    void defaultsExistingRowsAndRejectsUnsupportedLocale() throws Exception {
        try (MySQLContainer mysql = new MySQLContainer("mysql:8.4.10")) {
            mysql.start();
            Flyway.configure().dataSource(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()
            ).target("13").load().migrate();

            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()
            ); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO users (email, display_name, status, created_at, updated_at, version)
                        VALUES ('legacy@example.com', 'Legacy', 'ACTIVE', NOW(6), NOW(6), 0)
                        """);

                Flyway.configure().dataSource(
                        mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()
                ).load().migrate();

                try (var result = statement.executeQuery("""
                        SELECT preferred_locale FROM users
                        WHERE email = 'legacy@example.com'
                        """)) {
                    result.next();
                    assertEquals("en", result.getString(1));
                }
                assertEquals(1, statement.executeUpdate(
                        "UPDATE users SET preferred_locale = 'sk' WHERE email = 'legacy@example.com'"
                ));
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "UPDATE users SET preferred_locale = 'english' WHERE email = 'legacy@example.com'"
                ));
            }
        }
    }
}
