package com.trading.portfolio.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private final String url;

    public Database(String dbPath) {
        this.url = "jdbc:sqlite:" + dbPath;
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public void initSchema() {
        log.info("Initialising SQLite schema at {}", url);
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS accounts (
                    account_id   TEXT PRIMARY KEY,
                    customer_id  TEXT NOT NULL,
                    cash_balance REAL NOT NULL DEFAULT 0.0,
                    status       TEXT NOT NULL DEFAULT 'ACTIVE'
                )
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS holdings (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    account_id TEXT NOT NULL,
                    isin       TEXT NOT NULL,
                    quantity   REAL NOT NULL DEFAULT 0.0,
                    UNIQUE(account_id, isin),
                    FOREIGN KEY (account_id) REFERENCES accounts(account_id)
                )
                """);

            seedData(stmt);
            log.info("Schema ready.");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialise database", e);
        }
    }

    private void seedData(Statement stmt) throws SQLException {
        // Seed accounts – INSERT OR IGNORE keeps subsequent restarts idempotent
        stmt.executeUpdate("""
            INSERT OR IGNORE INTO accounts (account_id, customer_id, cash_balance, status) VALUES
                ('ACC-100001', '100001', 50000.00, 'ACTIVE'),
                ('ACC-100002', '100002',   500.00, 'ACTIVE'),
                ('ACC-100003', '100003', 25000.00, 'ACTIVE'),
                ('ACC-100004', '100004',100000.00, 'BLOCKED'),
                ('ACC-100005', '100005', 15000.00, 'ACTIVE')
            """);

        // Seed holdings
        stmt.executeUpdate("""
            INSERT OR IGNORE INTO holdings (account_id, isin, quantity) VALUES
                ('ACC-100001', 'DE0005140008', 100.0),
                ('ACC-100001', 'CH0038863350',  50.0),
                ('ACC-100003', 'DE0005140008', 200.0),
                ('ACC-100003', 'LU0392494562',  30.0),
                ('ACC-100005', 'DE000C1A2BC3',  25.0)
            """);
    }
}
