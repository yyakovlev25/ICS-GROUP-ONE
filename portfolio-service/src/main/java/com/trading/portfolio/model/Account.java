package com.trading.portfolio.model;

public class Account {

    private String accountId;
    private String customerId;
    private double cashBalance;
    private String status; // ACTIVE | BLOCKED

    public Account() {}

    public Account(String accountId, String customerId, double cashBalance, String status) {
        this.accountId = accountId;
        this.customerId = customerId;
        this.cashBalance = cashBalance;
        this.status = status;
    }

    public String getAccountId()          { return accountId; }
    public void setAccountId(String v)    { this.accountId = v; }

    public String getCustomerId()         { return customerId; }
    public void setCustomerId(String v)   { this.customerId = v; }

    public double getCashBalance()        { return cashBalance; }
    public void setCashBalance(double v)  { this.cashBalance = v; }

    public String getStatus()             { return status; }
    public void setStatus(String v)       { this.status = v; }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
