package com.agentbanking.platform.transaction;

import com.agentbanking.platform.audit.AuditService;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.transaction.TariffCalculator.TariffQuote;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TariffService {

    private final TariffRuleRepository repository;
    private final AuditService auditService;

    /** Default tariff book: deposits free for customers; withdrawal fees tiered; agents earn commission on all three. */
    public static List<TariffRule> defaultRules() {
        return List.of(
                TariffRule.of(TransactionType.CASH_DEPOSIT, "0.01", "999999999", "0", "0", "0.50", "0"),
                TariffRule.of(TransactionType.CASH_WITHDRAWAL, "0.01", "100.00", "1.50", "0.25", "0.60", "0"),
                TariffRule.of(TransactionType.CASH_WITHDRAWAL, "100.01", "1000.00", "1.25", "0", "0.50", "0"),
                TariffRule.of(TransactionType.CASH_WITHDRAWAL, "1000.01", "999999999", "1.00", "0", "0.40", "0"),
                TariffRule.of(TransactionType.AGENT_TRANSFER_TO_CUSTOMER, "0.01", "999999999", "0", "0", "0.25", "0"));
    }

    @Transactional
    public void ensureDefaults() {
        if (repository.count() == 0) {
            repository.saveAll(defaultRules());
        }
    }

    @Transactional
    public void restoreDefaults() {
        repository.deleteAllInBatch();
        repository.saveAll(defaultRules());
    }

    @Transactional(readOnly = true)
    public TariffQuote quote(TransactionType type, BigDecimal amount) {
        List<TariffRule> rules = repository.findByTransactionTypeAndActiveTrueOrderByMinAmount(type);
        TariffRule rule = TariffCalculator.match(rules, amount)
                .orElseThrow(() -> BusinessException.unprocessable("TARIFF_NOT_CONFIGURED",
                        "No active tariff configured for " + type + " amount " + amount));
        return TariffCalculator.calculate(rule, amount);
    }

    @Transactional(readOnly = true)
    public List<TariffResponse> list() {
        return repository.findAllByOrderByTransactionTypeAscMinAmountAsc().stream().map(TariffResponse::from).toList();
    }

    @Transactional
    public TariffResponse create(TariffRequest request, String username) {
        TariffRule rule = new TariffRule();
        apply(rule, request, username);
        assertNoOverlap(rule);
        repository.save(rule);
        auditService.record("TARIFF_CREATED", "TARIFF_RULE", rule.getId(), describe(rule));
        return TariffResponse.from(rule);
    }

    @Transactional
    public TariffResponse update(UUID id, TariffRequest request, String username) {
        TariffRule rule = repository.findById(id).orElseThrow(() -> BusinessException.notFound("Tariff rule", id));
        Map<String, Object> before = describe(rule);
        apply(rule, request, username);
        assertNoOverlap(rule);
        auditService.record("TARIFF_UPDATED", "TARIFF_RULE", rule.getId(), Map.of("before", before, "after", describe(rule)));
        return TariffResponse.from(rule);
    }

    private void apply(TariffRule rule, TariffRequest r, String username) {
        if (!r.transactionType().isCustomerTransaction()) {
            throw BusinessException.badRequest("INVALID_TARIFF_TYPE", "Tariffs apply only to customer transaction types");
        }
        if (r.maxAmount().compareTo(r.minAmount()) < 0) {
            throw BusinessException.badRequest("INVALID_TARIFF_RANGE", "maxAmount must be >= minAmount");
        }
        rule.setTransactionType(r.transactionType());
        rule.setMinAmount(r.minAmount());
        rule.setMaxAmount(r.maxAmount());
        rule.setFeePercent(r.feePercent());
        rule.setFeeFlat(r.feeFlat());
        rule.setCommissionPercent(r.commissionPercent());
        rule.setCommissionFlat(r.commissionFlat());
        rule.setActive(r.active() == null || r.active());
        rule.setUpdatedBy(username);
    }

    private void assertNoOverlap(TariffRule candidate) {
        if (!candidate.isActive()) {
            return;
        }
        boolean overlaps = repository.findByTransactionTypeAndActiveTrueOrderByMinAmount(candidate.getTransactionType()).stream()
                .filter(r -> !r.getId().equals(candidate.getId()))
                .anyMatch(r -> candidate.getMinAmount().compareTo(r.getMaxAmount()) <= 0
                        && candidate.getMaxAmount().compareTo(r.getMinAmount()) >= 0);
        if (overlaps) {
            throw BusinessException.conflict("TARIFF_OVERLAP", "Active tariff bands for a transaction type may not overlap");
        }
    }

    private Map<String, Object> describe(TariffRule r) {
        return Map.of("type", r.getTransactionType().name(), "min", r.getMinAmount(), "max", r.getMaxAmount(),
                "feePercent", r.getFeePercent(), "feeFlat", r.getFeeFlat(),
                "commissionPercent", r.getCommissionPercent(), "commissionFlat", r.getCommissionFlat(), "active", r.isActive());
    }

    public record TariffRequest(
            @NotNull TransactionType transactionType,
            @NotNull @DecimalMin("0") BigDecimal minAmount,
            @NotNull @DecimalMin("0.01") BigDecimal maxAmount,
            @NotNull @DecimalMin("0") BigDecimal feePercent,
            @NotNull @DecimalMin("0") BigDecimal feeFlat,
            @NotNull @DecimalMin("0") BigDecimal commissionPercent,
            @NotNull @DecimalMin("0") BigDecimal commissionFlat,
            Boolean active) {
    }

    public record TariffResponse(UUID id, TransactionType transactionType, BigDecimal minAmount, BigDecimal maxAmount,
                                 BigDecimal feePercent, BigDecimal feeFlat, BigDecimal commissionPercent,
                                 BigDecimal commissionFlat, boolean active, Instant updatedAt, String updatedBy) {
        static TariffResponse from(TariffRule r) {
            return new TariffResponse(r.getId(), r.getTransactionType(), r.getMinAmount(), r.getMaxAmount(), r.getFeePercent(),
                    r.getFeeFlat(), r.getCommissionPercent(), r.getCommissionFlat(), r.isActive(), r.getUpdatedAt(), r.getUpdatedBy());
        }
    }
}
