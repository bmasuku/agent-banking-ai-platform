package com.agentbanking.platform.fraud;

import com.agentbanking.platform.transaction.TransactionType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Behavioural features extracted from the database for one prospective transaction. */
public record FraudFeatures(
        UUID agentId,
        String agentCode,
        TransactionType transactionType,
        BigDecimal amount,
        int hourOfDay,
        boolean offHours,
        long agentTxCount1h,
        long agentTxCount24h,
        long agentHistoryCount30d,
        BigDecimal agentAvgAmount30d,
        BigDecimal agentStdAmount30d,
        double amountZScore,
        double amountToAvgRatio,
        boolean nearReportingThreshold,
        long nearThresholdCount1h,
        long customerTxCount24h,
        long customerDistinctAgents24h,
        boolean newCustomer,
        String agentRiskLevel,
        int agentCreditScore,
        long agentOpenFlags,
        long priorFlagged90d,
        long confirmedFraudCount,
        BigDecimal agentLatitude,
        BigDecimal agentLongitude,
        BigDecimal transactionLatitude,
        BigDecimal transactionLongitude,
        BigDecimal distanceFromRegisteredKm) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("transactionType", transactionType.name());
        m.put("amount", amount);
        m.put("hourOfDay", hourOfDay);
        m.put("offHours", offHours);
        m.put("agentTxCount1h", agentTxCount1h);
        m.put("agentTxCount24h", agentTxCount24h);
        m.put("agentHistoryCount30d", agentHistoryCount30d);
        m.put("agentAvgAmount30d", agentAvgAmount30d);
        m.put("agentStdAmount30d", agentStdAmount30d);
        m.put("amountZScore", round(amountZScore));
        m.put("amountToAvgRatio", round(amountToAvgRatio));
        m.put("nearReportingThreshold", nearReportingThreshold);
        m.put("nearThresholdCount1h", nearThresholdCount1h);
        m.put("customerTxCount24h", customerTxCount24h);
        m.put("customerDistinctAgents24h", customerDistinctAgents24h);
        m.put("newCustomer", newCustomer);
        m.put("agentRiskLevel", agentRiskLevel);
        m.put("agentCreditScore", agentCreditScore);
        m.put("agentOpenFlags", agentOpenFlags);
        m.put("priorFlagged90d", priorFlagged90d);
        m.put("confirmedFraudCount", confirmedFraudCount);
        m.put("agentLatitude", agentLatitude);
        m.put("agentLongitude", agentLongitude);
        m.put("transactionLatitude", transactionLatitude);
        m.put("transactionLongitude", transactionLongitude);
        m.put("distanceFromRegisteredKm", distanceFromRegisteredKm);
        return m;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
