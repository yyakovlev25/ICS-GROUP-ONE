package com.trading.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.trading.portfolio.model.Account;
import com.trading.portfolio.model.Holding;
import com.trading.portfolio.repository.AccountRepository;
import com.trading.portfolio.repository.HoldingRepository;
import com.trading.shared.client.PmsClient;
import com.trading.shared.dto.OrderValidationRequest;
import com.trading.shared.dto.OrderValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final PmsClient pms;
    private final AccountRepository accounts;
    private final HoldingRepository holdings;

    public PortfolioService(PmsClient pms, AccountRepository accounts, HoldingRepository holdings) {
        this.pms = pms;
        this.accounts = accounts;
        this.holdings = holdings;
    }

    // ── Proxy customer data from PMS ──────────────────────────────────────────

    public Optional<JsonNode> getAllCustomers() {
        return pms.getAllCustomers();
    }

    public Optional<JsonNode> getCustomer(String customerId) {
        return pms.getCustomer(customerId);
    }

    // ── Local account + holdings data ─────────────────────────────────────────

    public List<Account> getAllAccounts() throws SQLException {
        return accounts.findAll();
    }

    public Optional<Account> getAccount(String accountId) throws SQLException {
        return accounts.findById(accountId);
    }

    public Map<String, Object> getPortfolio(String accountId) throws SQLException {
        Optional<Account> accountOpt = accounts.findById(accountId);
        if (accountOpt.isEmpty()) return null;

        Account account = accountOpt.get();
        List<Holding> holdingList = holdings.findByAccountId(accountId);

        Map<String, Object> result = new HashMap<>();
        result.put("accountId", account.getAccountId());
        result.put("customerId", account.getCustomerId());
        result.put("cashBalance", account.getCashBalance());
        result.put("status", account.getStatus());
        result.put("holdings", holdingList);
        return result;
    }

    // ── Order validation ──────────────────────────────────────────────────────

    public OrderValidationResult validateOrder(OrderValidationRequest req) {
        String cid = req.customerId;
        String aid = req.accountId;

        // 1. Customer must exist in PMS
        Optional<JsonNode> customerNode = pms.getCustomer(cid);
        if (customerNode.isEmpty()) {
            return OrderValidationResult.fail(cid, aid,
                    "Customer '" + cid + "' not found in PMS");
        }

        // 2. Check customer active status from PMS response (field may vary by PMS version)
        JsonNode customer = customerNode.get();
        String customerStatus = extractStatus(customer);
        if (customerStatus != null && isBlockedStatus(customerStatus)) {
            return OrderValidationResult.fail(cid, aid,
                    "Customer '" + cid + "' is not active in PMS (status: " + customerStatus + ")");
        }

        // 3. Account must exist locally
        Account account;
        try {
            Optional<Account> accountOpt = accounts.findById(aid);
            if (accountOpt.isEmpty()) {
                return OrderValidationResult.fail(cid, aid,
                        "Account '" + aid + "' not found");
            }
            account = accountOpt.get();
        } catch (SQLException e) {
            log.error("DB error looking up account {}", aid, e);
            return OrderValidationResult.fail(cid, aid, "Internal error checking account");
        }

        // 4. Account must be ACTIVE
        if (!account.isActive()) {
            return OrderValidationResult.fail(cid, aid,
                    "Account '" + aid + "' is " + account.getStatus() + " – trading not permitted");
        }

        // 5. Account must belong to this customer
        if (!account.getCustomerId().equals(cid)) {
            return OrderValidationResult.fail(cid, aid,
                    "Account '" + aid + "' does not belong to customer '" + cid + "'");
        }

        // 6. Side-specific checks
        if ("BUY".equalsIgnoreCase(req.side)) {
            return checkBuy(req, account);
        } else if ("SELL".equalsIgnoreCase(req.side)) {
            return checkSell(req, account);
        } else {
            return OrderValidationResult.fail(cid, aid, "Unknown side '" + req.side + "' – expected BUY or SELL");
        }
    }

    private OrderValidationResult checkBuy(OrderValidationRequest req, Account account) {
        if (req.pricePerUnit > 0) {
            double requiredCash = req.quantity * req.pricePerUnit;
            if (account.getCashBalance() < requiredCash) {
                OrderValidationResult r = OrderValidationResult.fail(req.customerId, req.accountId,
                        String.format("Insufficient cash: need %.2f EUR, available %.2f EUR",
                                requiredCash, account.getCashBalance()));
                r.cashBalance = account.getCashBalance();
                return r;
            }
        }
        OrderValidationResult r = OrderValidationResult.ok(req.customerId, req.accountId,
                account.getStatus(), account.getCashBalance());
        if (req.pricePerUnit <= 0) {
            r.warnings.add("No price provided – cash balance check skipped (market order)");
        }
        return r;
    }

    private OrderValidationResult checkSell(OrderValidationRequest req, Account account) {
        try {
            Optional<Holding> holding = holdings.findByAccountAndIsin(req.accountId, req.isin);
            if (holding.isEmpty() || holding.get().getQuantity() < req.quantity) {
                double available = holding.map(Holding::getQuantity).orElse(0.0);
                OrderValidationResult r = OrderValidationResult.fail(req.customerId, req.accountId,
                        String.format("Insufficient holdings for %s: need %.2f, available %.2f",
                                req.isin, req.quantity, available));
                r.cashBalance = account.getCashBalance();
                return r;
            }
        } catch (SQLException e) {
            log.error("DB error looking up holdings", e);
            return OrderValidationResult.fail(req.customerId, req.accountId,
                    "Internal error checking holdings");
        }
        return OrderValidationResult.ok(req.customerId, req.accountId,
                account.getStatus(), account.getCashBalance());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractStatus(JsonNode node) {
        for (String field : new String[]{"status", "customerStatus", "accountStatus", "state"}) {
            JsonNode v = node.get(field);
            if (v != null && !v.isNull()) return v.asText();
        }
        return null;
    }

    private boolean isBlockedStatus(String status) {
        String s = status.toUpperCase();
        return s.equals("BLOCKED") || s.equals("INACTIVE") || s.equals("SUSPENDED") || s.equals("CLOSED");
    }
}
