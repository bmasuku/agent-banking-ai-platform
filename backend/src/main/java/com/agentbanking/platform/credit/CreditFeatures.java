package com.agentbanking.platform.credit;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Behavioural and repayment features used to score an agent's creditworthiness. */
public record CreditFeatures(
        UUID agentId,
        String agentCode,
        long tenureDays,
        long activeDays30d,
        long transactionCount30d,
        BigDecimal transactionVolume30d,
        BigDecimal avgDailyCommission30d,
        double commissionVolatility,
        BigDecimal efloatBalance,
        BigDecimal cashBalance,
        BigDecimal outstandingPrincipal,
        BigDecimal outstandingInterest,
        long advancesRepaid,
        long advancesRepaidLate,
        long advancesOverdue,
        long advancesDefaulted,
        long flaggedTransactions30d,
        long confirmedFraudCount,
        long openInvestigations,
        String agentStatus,
        BigDecimal requestedAmount) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenureDays", tenureDays);
        m.put("activeDays30d", activeDays30d);
        m.put("transactionCount30d", transactionCount30d);
        m.put("transactionVolume30d", transactionVolume30d);
        m.put("avgDailyCommission30d", avgDailyCommission30d);
        m.put("commissionVolatility", Math.round(commissionVolatility * 1000.0) / 1000.0);
        m.put("efloatBalance", efloatBalance);
        m.put("cashBalance", cashBalance);
        m.put("outstandingPrincipal", outstandingPrincipal);
        m.put("outstandingInterest", outstandingInterest);
        m.put("advancesRepaid", advancesRepaid);
        m.put("advancesRepaidLate", advancesRepaidLate);
        m.put("advancesOverdue", advancesOverdue);
        m.put("advancesDefaulted", advancesDefaulted);
        m.put("flaggedTransactions30d", flaggedTransactions30d);
        m.put("confirmedFraudCount", confirmedFraudCount);
        m.put("openInvestigations", openInvestigations);
        m.put("agentStatus", agentStatus);
        m.put("requestedAmount", requestedAmount);
        return m;
    }
}
