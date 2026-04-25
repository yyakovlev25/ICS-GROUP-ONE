package com.trading.portfolio.repository;

import com.trading.portfolio.db.Database;
import com.trading.portfolio.model.Account;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AccountRepository {

    private final Database db;

    public AccountRepository(Database db) {
        this.db = db;
    }

    public List<Account> findAll() throws SQLException {
        List<Account> list = new ArrayList<>();
        try (Connection conn = db.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM accounts ORDER BY account_id")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public Optional<Account> findById(String accountId) throws SQLException {
        String sql = "SELECT * FROM accounts WHERE account_id = ?";
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Account> findByCustomerId(String customerId) throws SQLException {
        String sql = "SELECT * FROM accounts WHERE customer_id = ? LIMIT 1";
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private Account map(ResultSet rs) throws SQLException {
        return new Account(
                rs.getString("account_id"),
                rs.getString("customer_id"),
                rs.getDouble("cash_balance"),
                rs.getString("status")
        );
    }
}
