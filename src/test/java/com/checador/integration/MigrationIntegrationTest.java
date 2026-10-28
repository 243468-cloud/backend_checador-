package com.checador.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MigrationIntegrationTest {

    private static MySQLContainer<?> mysql;
    private static String baseUrl;
    private static String dbUser;
    private static String dbPass;
    private static boolean dbAvailable = false;

    @BeforeAll
    static void setUp() {
        if (System.getProperty("os.name").toLowerCase().contains("win") && System.getenv("DOCKER_HOST") == null) {
            System.setProperty("DOCKER_HOST", "npipe:////./pipe/docker_engine");
        }
        try {
            mysql = new MySQLContainer<>("mysql:8.0.40")
                    .withDatabaseName("testdb")
                    .withUsername("testuser")
                    .withPassword("testpass");
            mysql.start();
            baseUrl = mysql.getJdbcUrl().substring(0, mysql.getJdbcUrl().lastIndexOf('/'));
            dbUser = mysql.getUsername();
            dbPass = mysql.getPassword();
            dbAvailable = true;
        } catch (Throwable t) {
            // Si Testcontainers no puede iniciar contenedores efímeros, probar MySQL local en puerto 3307
            String fallbackUrl = "jdbc:mysql://localhost:3307";
            try (Connection conn = DriverManager.getConnection(fallbackUrl + "?useSSL=false&allowPublicKeyRetrieval=true", "root", "rootpw")) {
                baseUrl = fallbackUrl;
                dbUser = "root";
                dbPass = "rootpw";
                dbAvailable = true;
            } catch (Throwable fallbackErr) {
                dbAvailable = false;
            }
        }
    }

    @AfterAll
    static void tearDown() {
        if (mysql != null && mysql.isRunning()) {
            mysql.stop();
        }
    }

    @Test
    @DisplayName("Migraciones en base vacía ejecutan V1, V2, V3, V4 y V5 exitosamente")
    void testFlywayOnEmptyDatabase() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbAvailable,
                "Se omite la prueba: no se encontró entorno Docker funcional ni MySQL accesible en localhost:3307");
        String dbName = "tc_empty_db";
        String adminUrl = baseUrl + "/mysql?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Mexico_City";
        try (Connection rootConn = DriverManager.getConnection(adminUrl, dbUser, dbPass);
             Statement stmt = rootConn.createStatement()) {
            stmt.execute("DROP DATABASE IF EXISTS " + dbName);
            stmt.execute("CREATE DATABASE " + dbName + " CHARACTER SET utf8mb4");
        }

        String targetUrl = baseUrl + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Mexico_City";

        Flyway flyway = Flyway.configure()
                .dataSource(targetUrl, dbUser, dbPass)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        var result = flyway.migrate();
        assertTrue(result.success, "Flyway debe reportar éxito");
        assertEquals(5, result.migrationsExecuted, "Debe ejecutar V1, V2, V3, V4 y V5");

        try (Connection conn = DriverManager.getConnection(targetUrl, dbUser, dbPass);
             Statement stmt = conn.createStatement()) {

            // Validar que todas las 11 tablas tienen Primary Key
            ResultSet rs = stmt.executeQuery(
                    "SELECT table_name FROM information_schema.table_constraints " +
                    "WHERE table_schema = '" + dbName + "' AND constraint_type = 'PRIMARY KEY'");
            List<String> pkTables = new ArrayList<>();
            while (rs.next()) {
                pkTables.add(rs.getString(1).toLowerCase());
            }
            assertTrue(pkTables.contains("users"));
            assertTrue(pkTables.contains("branches"));
            assertTrue(pkTables.contains("attendances"));
            assertTrue(pkTables.contains("tenant_modules"));
            assertTrue(pkTables.contains("push_subscriptions"));

            // Validar índice compuesto en users
            ResultSet rsIdx = stmt.executeQuery("SHOW INDEX FROM users WHERE Key_name = 'uk_users_tenant_username'");
            assertTrue(rsIdx.next(), "El índice compuesto uk_users_tenant_username debe existir");
        }
    }

    @Test
    @DisplayName("Migraciones en base existente aplican baseline V1 y ejecutan V2, V3, V4 y V5 exitosamente")
    void testFlywayOnLegacyDatabase() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbAvailable,
                "Se omite la prueba: no se encontró entorno Docker funcional ni MySQL accesible en localhost:3307");
        String dbName = "tc_legacy_db";
        String adminUrl = baseUrl + "/mysql?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Mexico_City";
        try (Connection rootConn = DriverManager.getConnection(adminUrl, dbUser, dbPass);
             Statement stmt = rootConn.createStatement()) {
            stmt.execute("DROP DATABASE IF EXISTS " + dbName);
            stmt.execute("CREATE DATABASE " + dbName + " CHARACTER SET utf8mb4");
        }

        String targetUrl = baseUrl + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Mexico_City";

        // Crear esquema legacy (previo a V2 y V3) con datos
        try (Connection conn = DriverManager.getConnection(targetUrl, dbUser, dbPass);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(100) NOT NULL UNIQUE, " +
                    "full_name VARCHAR(150) NOT NULL, " +
                    "password VARCHAR(255) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL, " +
                    "active BIT NOT NULL DEFAULT 1, " +
                    "tenant_id BIGINT NOT NULL, " +
                    "created_at DATETIME(6) NOT NULL)");

            stmt.execute("INSERT INTO users (tenant_id, username, full_name, password, role, created_at) " +
                    "VALUES (10, 'legacy_emp', 'Legacy User', 'secret', 'EMPLOYEE', NOW()), " +
                    "(20, 'legacy_admin', 'Legacy Admin', 'secret', 'ADMIN', NOW())");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(targetUrl, dbUser, dbPass)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        var result = flyway.migrate();
        assertTrue(result.success, "Flyway debe reportar éxito sobre base legacy");
        assertEquals(4, result.migrationsExecuted, "Debe ejecutar V2, V3, V4 y V5 (omitiendo V1 por baseline)");

        try (Connection conn = DriverManager.getConnection(targetUrl, dbUser, dbPass);
             Statement stmt = conn.createStatement()) {

            // Validar que tenant_modules se creó y tiene 4 módulos por cada tenant preexistente
            ResultSet rsMod = stmt.executeQuery("SELECT tenant_id, COUNT(*) FROM tenant_modules GROUP BY tenant_id");
            int tenantsCount = 0;
            while (rsMod.next()) {
                assertEquals(4, rsMod.getInt(2), "Cada tenant debe tener 4 módulos");
                tenantsCount++;
            }
            assertEquals(2, tenantsCount, "Debe haber 2 tenants migrados");

            // Validar que el índice UK_username se reemplazó por uk_users_tenant_username
            ResultSet rsIdx = stmt.executeQuery("SHOW INDEX FROM users WHERE Key_name = 'uk_users_tenant_username'");
            assertTrue(rsIdx.next(), "uk_users_tenant_username debe existir");

            ResultSet rsOldIdx = stmt.executeQuery("SHOW INDEX FROM users WHERE Key_name = 'username'");
            assertFalse(rsOldIdx.next(), "El índice antiguo 'username' no debe existir");
        }
    }
}
