package com.agentbanking.platform.admin;

import com.agentbanking.platform.config.AppProperties;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.transaction.TariffService;
import com.agentbanking.platform.user.Role;
import com.agentbanking.platform.user.UserRepository;
import com.agentbanking.platform.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ensures reference data exists on startup (settings, tariffs, system ledger accounts) and, on an empty
 * database, either seeds the demonstration dataset or creates only the bootstrap administrator.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class PlatformBootstrap implements ApplicationRunner {

    private final AppProperties properties;
    private final SystemSettingService settingService;
    private final TariffService tariffService;
    private final LedgerAccountService ledgerAccountService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final DemoDataSeeder demoDataSeeder;

    @Override
    public void run(ApplicationArguments args) {
        initializeReferenceData();
        if (userRepository.count() > 0) {
            return;
        }
        if (properties.getDemo().isSeedOnStartup()) {
            log.info("Empty database: seeding demonstration data ({} days of history)", properties.getDemo().getHistoryDays());
            demoDataSeeder.seed();
        } else {
            AppProperties.Bootstrap b = properties.getBootstrap();
            userRepository.save(userService.buildUser(b.getAdminUsername(), b.getAdminEmail(), "Platform Administrator", null,
                    Role.ADMIN, b.getAdminPassword(), null));
            log.info("Empty database: created bootstrap administrator '{}'", b.getAdminUsername());
        }
    }

    public void initializeReferenceData() {
        settingService.ensureDefaults();
        tariffService.ensureDefaults();
        ledgerAccountService.ensureSystemAccounts();
    }
}
