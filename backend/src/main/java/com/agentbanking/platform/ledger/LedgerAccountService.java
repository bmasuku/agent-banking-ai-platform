package com.agentbanking.platform.ledger;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.area.Area;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.config.AppProperties;
import com.agentbanking.platform.customer.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Provisions and looks up ledger accounts. Never changes balances (see {@link LedgerService}). */
@Service
@RequiredArgsConstructor
public class LedgerAccountService {

    public static final List<BalanceType> AGENT_BALANCE_TYPES = List.of(BalanceType.EFLOAT, BalanceType.CASH,
            BalanceType.COMMISSION_PAYABLE, BalanceType.ADVANCE_PRINCIPAL, BalanceType.ADVANCE_INTEREST);

    private final LedgerAccountRepository repository;
    private final AppProperties properties;

    @Transactional
    public void ensureSystemAccounts() {
        Arrays.stream(BalanceType.values())
                .filter(t -> t.ownerType() == OwnerType.SYSTEM)
                .forEach(type -> {
                    if (repository.findByOwnerTypeAndBalanceType(OwnerType.SYSTEM, type).isEmpty()) {
                        repository.save(new LedgerAccount("SYS-" + type.name().replace('_', '-'), type.label(), type,
                                null, null, null, properties.getCurrency()));
                    }
                });
    }

    @Transactional
    public void provisionAgentAccounts(Agent agent) {
        for (BalanceType type : AGENT_BALANCE_TYPES) {
            if (repository.findByAgentIdAndBalanceType(agent.getId(), type).isEmpty()) {
                repository.save(new LedgerAccount(agent.getAgentCode() + "-" + type.name().replace('_', '-'),
                        agent.getAgentCode() + " " + type.label(), type, agent.getId(), null, null, properties.getCurrency()));
            }
        }
    }

    @Transactional
    public void provisionAreaAccounts(Area area) {
        for (BalanceType type : List.of(BalanceType.AREA_EFLOAT_POOL, BalanceType.AREA_CASH_POOL)) {
            if (repository.findByAreaIdAndBalanceType(area.getId(), type).isEmpty()) {
                repository.save(new LedgerAccount("AREA-" + area.getCode() + "-" + type.name().replace('_', '-'),
                        area.getName() + " " + type.label(), type, null, area.getId(), null, properties.getCurrency()));
            }
        }
    }

    @Transactional
    public LedgerAccount provisionCustomerWallet(Customer customer) {
        return repository.findByCustomerIdAndBalanceType(customer.getId(), BalanceType.CUSTOMER_WALLET)
                .orElseGet(() -> repository.save(new LedgerAccount(customer.getCustomerNumber() + "-WALLET",
                        customer.getFullName() + " wallet", BalanceType.CUSTOMER_WALLET, null, null, customer.getId(),
                        properties.getCurrency())));
    }

    @Transactional(readOnly = true)
    public LedgerAccount system(BalanceType type) {
        return repository.findByOwnerTypeAndBalanceType(OwnerType.SYSTEM, type)
                .orElseThrow(() -> missing("system " + type));
    }

    @Transactional(readOnly = true)
    public LedgerAccount agent(UUID agentId, BalanceType type) {
        return repository.findByAgentIdAndBalanceType(agentId, type).orElseThrow(() -> missing("agent " + agentId + " " + type));
    }

    @Transactional(readOnly = true)
    public LedgerAccount area(UUID areaId, BalanceType type) {
        return repository.findByAreaIdAndBalanceType(areaId, type).orElseThrow(() -> missing("area " + areaId + " " + type));
    }

    @Transactional(readOnly = true)
    public LedgerAccount customerWallet(UUID customerId) {
        return repository.findByCustomerIdAndBalanceType(customerId, BalanceType.CUSTOMER_WALLET)
                .orElseThrow(() -> missing("customer wallet " + customerId));
    }

    @Transactional(readOnly = true)
    public BalanceSnapshot agentBalances(UUID agentId) {
        Map<BalanceType, BigDecimal> map = new EnumMap<>(BalanceType.class);
        repository.findByAgentId(agentId).forEach(a -> map.put(a.getBalanceType(), a.getBalance()));
        return new BalanceSnapshot(
                Money.of(map.getOrDefault(BalanceType.EFLOAT, BigDecimal.ZERO)),
                Money.of(map.getOrDefault(BalanceType.CASH, BigDecimal.ZERO)),
                Money.of(map.getOrDefault(BalanceType.COMMISSION_PAYABLE, BigDecimal.ZERO)),
                Money.of(map.getOrDefault(BalanceType.ADVANCE_PRINCIPAL, BigDecimal.ZERO)),
                Money.of(map.getOrDefault(BalanceType.ADVANCE_INTEREST, BigDecimal.ZERO)));
    }

    @Transactional(readOnly = true)
    public BigDecimal areaBalance(UUID areaId, BalanceType type) {
        return repository.findByAreaIdAndBalanceType(areaId, type).map(a -> Money.of(a.getBalance())).orElse(Money.ZERO);
    }

    @Transactional(readOnly = true)
    public BigDecimal customerBalance(UUID customerId) {
        return repository.findByCustomerIdAndBalanceType(customerId, BalanceType.CUSTOMER_WALLET)
                .map(a -> Money.of(a.getBalance())).orElse(Money.ZERO);
    }

    private BusinessException missing(String what) {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "LEDGER_ACCOUNT_MISSING", "Ledger account not provisioned: " + what);
    }
}
