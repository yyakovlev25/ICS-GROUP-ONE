package com.trading.shared.dto;

import java.util.ArrayList;
import java.util.List;

/** Response from POST /check on the compliance-service. */
public class ComplianceCheckResult {

    public boolean approved;
    public String customerId;
    public String isin;
    public String instrumentName;
    public String productType;
    public List<String> rejectionReasons = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();

    public static ComplianceCheckResult approve(String customerId, String isin,
                                                String instrumentName, String productType) {
        ComplianceCheckResult r = new ComplianceCheckResult();
        r.approved = true;
        r.customerId = customerId;
        r.isin = isin;
        r.instrumentName = instrumentName;
        r.productType = productType;
        return r;
    }

    public static ComplianceCheckResult reject(String customerId, String isin, String... reasons) {
        ComplianceCheckResult r = new ComplianceCheckResult();
        r.approved = false;
        r.customerId = customerId;
        r.isin = isin;
        for (String reason : reasons) r.rejectionReasons.add(reason);
        return r;
    }
}
