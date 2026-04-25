package com.trading.portfolio.repository;

import com.trading.portfolio.db.Database;
import com.trading.portfolio.model.Holding;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HoldingRepository {

    private final Database db;

    public HoldingRepository(Database db) {
        this.db = db;
    }

    public List<Holding> findByAccountId(String accountId) throws SQLException {
        List<Holding> list = new ArrayList<>();
        String sql = "SELECT * FROM holdings WHERE account_id = ? ORDER BY isin";
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public Optional<Holding> findByAccountAndIsin(String accountId, String isin) throws SQLException {
        String sql = "SELECT * FROM holdings WHERE account_id = ? AND isin = ?";
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountId);
            ps.setString(2, isin);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private Holding map(ResultSet rs) throws SQLException {
        return new Holding(
                rs.getString("account_id"),
                rs.getString("isin"),
                rs.getDouble("quantity")
        );
    }
}
