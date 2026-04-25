package com.trading.portfolio.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * H2 in-memory database.
 * Named "portfolio" so all JDBC connections within the same JVM share the same DB.
 * DB_CLOSE_DELAY=-1 keeps the data alive as long as the JVM runs.
 */
public class Database {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private static final String URL =
            "jdbc:h2:mem:portfolio;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public void initSchema() {
        log.info("Initialising in-memory H2 database...");
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS accounts (
                    account_id   VARCHAR(50) PRIMARY KEY,
                    customer_id  VARCHAR(50) NOT NULL,
                    cash_balance DOUBLE      NOT NULL DEFAULT 0.0,
                    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                )
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS holdings (
                    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    account_id VARCHAR(50) NOT NULL,
                    isin       VARCHAR(20) NOT NULL,
                    quantity   DOUBLE      NOT NULL DEFAULT 0.0,
                    UNIQUE(account_id, isin)
                )
                """);

            seedData(stmt);
            log.info("H2 schema ready. Demo accounts loaded.");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialise H2 database", e);
        }
    }

    private void seedData(Statement stmt) throws SQLException {
        // Fresh in-memory DB on every start → plain INSERT, no conflict handling needed
        stmt.executeUpdate("""
            INSERT INTO accounts (account_id, customer_id, cash_balance, status) VALUES
                ('ACC-100001', '100001',  50000.00, 'ACTIVE'),
                ('ACC-100002', '100002',    500.00, 'ACTIVE'),
                ('ACC-100003', '100003',  25000.00, 'ACTIVE'),
                ('ACC-100004', '100004', 100000.00, 'BLOCKED'),
                ('ACC-100005', '100005',  15000.00, 'ACTIVE')
            """);

        stmt.executeUpdate("""
            INSERT INTO holdings (account_id, isin, quantity) VALUES
                ('ACC-100001', 'DE0005140008', 100.0),
                ('ACC-100001', 'CH0038863350',  50.0),
                ('ACC-100003', 'DE0005140008', 200.0),
                ('ACC-100003', 'LU0392494562',  30.0),
                ('ACC-100005', 'DE000C1A2BC3',  25.0)
            """);
    }
}
