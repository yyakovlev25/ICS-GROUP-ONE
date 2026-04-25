package com.trading.portfolio.model;

public class Holding {

    private String accountId;
    private String isin;
    private double quantity;

    public Holding() {}

    public Holding(String accountId, String isin, double quantity) {
        this.accountId = accountId;
        this.isin = isin;
        this.quantity = quantity;
    }

    public String getAccountId()         { return accountId; }
    public void setAccountId(String v)   { this.accountId = v; }

    public String getIsin()              { return isin; }
    public void setIsin(String v)        { this.isin = v; }

    public double getQuantity()          { return quantity; }
    public void setQuantity(double v)    { this.quantity = v; }
}
