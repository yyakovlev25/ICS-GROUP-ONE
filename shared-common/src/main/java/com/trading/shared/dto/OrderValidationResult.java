package com.trading.shared.dto;

import java.util.ArrayList;
import java.util.List;

/** Response from POST /validate on the portfolio-service. */
public class OrderValidationResult {

    public boolean valid;
    public String customerId;
    public String accountId;
    public String accountStatus;
    public Double cashBalance;
    public List<String> errors = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();

    public static OrderValidationResult ok(String customerId, String accountId,
                                           String status, double balance) {
        OrderValidationResult r = new OrderValidationResult();
        r.valid = true;
        r.customerId = customerId;
        r.accountId = accountId;
        r.accountStatus = status;
        r.cashBalance = balance;
        return r;
    }

    public static OrderValidationResult fail(String customerId, String accountId, String... reasons) {
        OrderValidationResult r = new OrderValidationResult();
        r.valid = false;
        r.customerId = customerId;
        r.accountId = accountId;
        for (String reason : reasons) r.errors.add(reason);
        return r;
    }
}
