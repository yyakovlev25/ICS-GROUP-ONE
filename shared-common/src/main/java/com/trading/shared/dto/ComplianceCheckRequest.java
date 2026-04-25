package com.trading.shared.dto;

/** Request body for POST /check on the compliance-service. */
public class ComplianceCheckRequest {

    /** PMS external customer id */
    public String customerId;

    /** ISIN of the instrument */
    public String isin;

    /** BUY or SELL */
    public String side;

    /** Number of units */
    public double quantity;

    /** Price per unit used for informational purposes */
    public double pricePerUnit;

    /**
     * Whether the customer explicitly confirmed risk for complex/risky products
     * (e.g. certificates, structured products).
     */
    public boolean riskConfirmed;
}
