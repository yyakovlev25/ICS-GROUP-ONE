package com.trading.shared.dto;

/**
 * Request body for POST /validate on the portfolio-service.
 * The caller must supply the current price per unit so balance can be checked.
 */
public class OrderValidationRequest {

    /** PMS external customer id, e.g. "100001" */
    public String customerId;

    /** Local account id, e.g. "ACC-100001" */
    public String accountId;

    /** ISIN of the instrument */
    public String isin;

    /** BUY or SELL */
    public String side;

    /** Number of units (must be > 0) */
    public double quantity;

    /**
     * Price per unit in EUR used for BUY cash-balance check.
     * Set to 0 or omit to skip the balance check (market-order scenario).
     */
    public double pricePerUnit;
}
