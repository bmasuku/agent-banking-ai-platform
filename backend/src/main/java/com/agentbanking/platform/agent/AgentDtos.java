package com.agentbanking.platform.agent;

import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.ledger.BalanceSnapshot;
import com.agentbanking.platform.prediction.LiquidityRisk;
import com.agentbanking.platform.user.UserDtos;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AgentDtos {

    private AgentDtos() {
    }

    public record AgentProfile(UUID id, String agentCode, String businessName, UUID userId, String fullName, String username,
                               String email, String phoneNumber, UUID areaId, String areaCode, String areaName, AgentTier tier,
                               AgentStatus status, RiskLevel riskLevel, int creditScore, BigDecimal creditLimit,
                               BigDecimal latitude, BigDecimal longitude, String address, BigDecimal minEfloatThreshold,
                               BigDecimal minCashThreshold, Instant onboardedAt, String accountNumber,
                               BigDecimal dailyTransactionLimit, AgentAccountStatus accountStatus) {
    }

    /**
     * @param availableCredit credit limit less outstanding advance principal
     * @param usedToday       customer transaction volume processed today (business time zone)
     */
    public record AgentBalanceView(UUID agentId, String agentCode, String businessName, String currency, BalanceSnapshot balances,
                                   BigDecimal minEfloatThreshold, BigDecimal minCashThreshold, boolean efloatLow, boolean cashLow,
                                   BigDecimal creditLimit, BigDecimal availableCredit, BigDecimal dailyTransactionLimit,
                                   BigDecimal usedToday, Instant asOf) {
    }

    public record AgentLocation(UUID agentId, String agentCode, String businessName, String address, BigDecimal latitude,
                                BigDecimal longitude, AgentStatus status, RiskLevel riskLevel, BigDecimal efloat, BigDecimal cash,
                                boolean efloatLow, boolean cashLow, LiquidityRisk predictedRisk) {
    }

    public record AgentSummary(UUID id, String agentCode, String businessName, String fullName, String areaCode,
                               AgentStatus status, RiskLevel riskLevel, AgentTier tier, int creditScore, BigDecimal efloat,
                               BigDecimal cash, BigDecimal commissionPayable, BigDecimal advanceOutstanding, boolean efloatLow,
                               boolean cashLow) {
    }

    public record CreateAgentRequest(
            @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "^[a-zA-Z0-9._-]+$") String username,
            @NotBlank @Email String email,
            @NotBlank @Size(max = 160) String fullName,
            @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Phone number must contain 9-15 digits") String phoneNumber,
            @NotBlank @Pattern(regexp = UserDtos.PASSWORD_PATTERN, message = UserDtos.PASSWORD_MESSAGE) String password,
            @NotBlank @Size(max = 160) String businessName,
            @NotNull UUID areaId,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @NotBlank @Size(max = 200) String address,
            @NotNull @DecimalMin("0") BigDecimal minEfloatThreshold,
            @NotNull @DecimalMin("0") BigDecimal minCashThreshold,
            @NotNull @DecimalMin("1.00") BigDecimal dailyTransactionLimit) {
    }

    public record UpdateAgentRequest(
            @Size(max = 160) String businessName,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @Size(max = 200) String address,
            @DecimalMin("0") BigDecimal minEfloatThreshold,
            @DecimalMin("0") BigDecimal minCashThreshold,
            @DecimalMin("1.00") BigDecimal dailyTransactionLimit) {
    }
}
