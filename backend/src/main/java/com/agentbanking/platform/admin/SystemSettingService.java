package com.agentbanking.platform.admin;

import com.agentbanking.platform.audit.AuditService;
import com.agentbanking.platform.commission.RecoveryOrder;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository repository;
    private final AuditService auditService;

    @Transactional
    public void ensureDefaults() {
        for (SettingKey key : SettingKey.values()) {
            if (!repository.existsById(key.key())) {
                SystemSetting s = new SystemSetting();
                s.setKey(key.key());
                s.setValue(key.defaultValue());
                s.setDefaultValue(key.defaultValue());
                s.setValueType(key.type().name());
                s.setCategory(key.category());
                s.setDescription(key.description());
                s.setUpdatedAt(AppClock.now());
                s.setUpdatedBy("system");
                repository.save(s);
            }
        }
    }

    @Transactional
    public void restoreDefaults() {
        repository.findAll().forEach(s -> {
            s.setValue(s.getDefaultValue());
            s.setUpdatedAt(AppClock.now());
            s.setUpdatedBy("system");
        });
        ensureDefaults();
    }

    @Transactional(readOnly = true)
    public String getString(SettingKey key) {
        return repository.findById(key.key()).map(SystemSetting::getValue).orElse(key.defaultValue());
    }

    public BigDecimal getDecimal(SettingKey key) {
        return new BigDecimal(getString(key));
    }

    public int getInt(SettingKey key) {
        return Integer.parseInt(getString(key));
    }

    public boolean getBoolean(SettingKey key) {
        return Boolean.parseBoolean(getString(key));
    }

    public RecoveryOrder getRecoveryOrder(SettingKey key) {
        return RecoveryOrder.valueOf(getString(key));
    }

    public List<BigDecimal> getDecimalList(SettingKey key) {
        return Arrays.stream(getString(key).split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(BigDecimal::new).toList();
    }

    @Transactional(readOnly = true)
    public List<SettingResponse> list() {
        return repository.findAllByOrderByCategoryAscKeyAsc().stream().map(SettingResponse::from).toList();
    }

    @Transactional
    public SettingResponse update(String rawKey, String value, String username) {
        SettingKey key = SettingKey.fromKey(rawKey)
                .orElseThrow(() -> BusinessException.notFound("Setting", rawKey));
        String normalized = validate(key, value);
        SystemSetting setting = repository.findById(key.key()).orElseThrow(() -> BusinessException.notFound("Setting", rawKey));
        String previous = setting.getValue();
        setting.setValue(normalized);
        setting.setUpdatedAt(AppClock.now());
        setting.setUpdatedBy(username);
        crossValidate();
        auditService.record("SETTING_UPDATED", "SYSTEM_SETTING", key.key(), Map.of("from", previous, "to", normalized));
        return SettingResponse.from(setting);
    }

    private String validate(SettingKey key, String value) {
        if (value == null || value.isBlank()) {
            throw BusinessException.badRequest("INVALID_SETTING_VALUE", "Value is required");
        }
        String v = value.trim();
        try {
            switch (key.type()) {
                case DECIMAL -> {
                    BigDecimal d = new BigDecimal(v);
                    if (d.signum() < 0) {
                        throw BusinessException.badRequest("INVALID_SETTING_VALUE", key.key() + " cannot be negative");
                    }
                    if (key == SettingKey.COMMISSION_RECOVERY_MAX_PERCENT && d.compareTo(BigDecimal.valueOf(100)) > 0) {
                        throw BusinessException.badRequest("INVALID_SETTING_VALUE", key.key() + " must be between 0 and 100");
                    }
                    if ((key == SettingKey.FRAUD_BLOCK_THRESHOLD || key == SettingKey.FRAUD_REVIEW_THRESHOLD)
                            && d.compareTo(BigDecimal.ONE) > 0) {
                        throw BusinessException.badRequest("INVALID_SETTING_VALUE", key.key() + " must be between 0 and 1");
                    }
                }
                case INTEGER -> {
                    if (Integer.parseInt(v) < 0) {
                        throw BusinessException.badRequest("INVALID_SETTING_VALUE", key.key() + " cannot be negative");
                    }
                }
                case BOOLEAN -> {
                    if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
                        throw BusinessException.badRequest("INVALID_SETTING_VALUE", key.key() + " must be true or false");
                    }
                    v = v.toLowerCase();
                }
                case ENUM -> v = RecoveryOrder.valueOf(v.toUpperCase()).name();
                case STRING -> {
                    if (key == SettingKey.FRAUD_REPORTING_THRESHOLDS) {
                        Arrays.stream(v.split(",")).map(String::trim).forEach(BigDecimal::new);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_SETTING_VALUE", "Invalid value for " + key.key() + ": " + value);
        }
        return v;
    }

    private void crossValidate() {
        if (getDecimal(SettingKey.FRAUD_REVIEW_THRESHOLD).compareTo(getDecimal(SettingKey.FRAUD_BLOCK_THRESHOLD)) > 0) {
            throw BusinessException.badRequest("INVALID_SETTING_VALUE", "fraud.review_threshold must not exceed fraud.block_threshold");
        }
    }

    public record SettingResponse(String key, String value, String defaultValue, String valueType, String category,
                                  String description, Instant updatedAt, String updatedBy) {
        static SettingResponse from(SystemSetting s) {
            return new SettingResponse(s.getKey(), s.getValue(), s.getDefaultValue(), s.getValueType(), s.getCategory(),
                    s.getDescription(), s.getUpdatedAt(), s.getUpdatedBy());
        }
    }
}
