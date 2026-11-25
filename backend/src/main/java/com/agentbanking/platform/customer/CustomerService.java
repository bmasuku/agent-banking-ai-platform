package com.agentbanking.platform.customer;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.ledger.LedgerAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerRepository repository;
    private final LedgerAccountService ledgerAccountService;

    public static String normalizePhone(String phone) {
        if (phone == null) {
            throw BusinessException.badRequest("INVALID_PHONE", "Customer phone number is required");
        }
        String normalized = phone.replaceAll("[\\s()-]", "");
        if (!normalized.matches("^\\+?[0-9]{9,15}$")) {
            throw BusinessException.badRequest("INVALID_PHONE", "Customer phone number must contain 9-15 digits");
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findByPhone(String phone) {
        return repository.findByPhoneNumber(normalizePhone(phone));
    }

    @Transactional(readOnly = true)
    public Customer requireActiveByPhone(String phone) {
        Customer customer = findByPhone(phone).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                "CUSTOMER_NOT_FOUND", "No registered customer with phone " + phone));
        requireActive(customer);
        return customer;
    }

    /** Finds a customer by phone or registers a new one (with an e-money wallet) at the serving agent. */
    @Transactional
    public Customer findOrRegister(String phone, String fullName, String nationalId, Agent registeringAgent) {
        String normalized = normalizePhone(phone);
        Optional<Customer> existing = repository.findByPhoneNumber(normalized);
        if (existing.isPresent()) {
            requireActive(existing.get());
            return existing.get();
        }
        if (fullName == null || fullName.isBlank()) {
            throw BusinessException.badRequest("CUSTOMER_NAME_REQUIRED",
                    "Customer name is required to register a new customer with phone " + normalized);
        }
        Customer customer = new Customer();
        customer.setCustomerNumber(nextCustomerNumber());
        customer.setFullName(fullName.trim());
        customer.setPhoneNumber(normalized);
        customer.setNationalId(nationalId);
        customer.setKycLevel(nationalId != null && !nationalId.isBlank() ? Customer.KycLevel.STANDARD : Customer.KycLevel.BASIC);
        customer.setRegisteredByAgent(registeringAgent);
        repository.save(customer);
        ledgerAccountService.provisionCustomerWallet(customer);
        return customer;
    }

    @Transactional(readOnly = true)
    public Customer require(UUID id) {
        return repository.findById(id).orElseThrow(() -> BusinessException.notFound("Customer", id));
    }

    private void requireActive(Customer customer) {
        if (customer.getStatus() != Customer.Status.ACTIVE) {
            throw BusinessException.unprocessable("CUSTOMER_BLOCKED", "Customer " + customer.getCustomerNumber() + " is blocked");
        }
    }

    private String nextCustomerNumber() {
        String candidate;
        do {
            candidate = "CUS-" + (10_000_000 + RANDOM.nextInt(90_000_000));
        } while (repository.existsByCustomerNumber(candidate));
        return candidate;
    }

    public record CustomerLookup(UUID id, String customerNumber, String fullName, String phoneNumber,
                                 Customer.KycLevel kycLevel, Customer.Status status) {
        public static CustomerLookup from(Customer c) {
            return new CustomerLookup(c.getId(), c.getCustomerNumber(), c.getFullName(), c.getPhoneNumber(),
                    c.getKycLevel(), c.getStatus());
        }
    }
}
