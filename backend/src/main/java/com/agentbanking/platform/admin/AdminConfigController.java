package com.agentbanking.platform.admin;

import com.agentbanking.platform.admin.DemoResetService.ResetResult;
import com.agentbanking.platform.admin.SystemSettingService.SettingResponse;
import com.agentbanking.platform.ai.AiServiceClient;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.security.UserPrincipal;
import com.agentbanking.platform.transaction.TariffService;
import com.agentbanking.platform.transaction.TariffService.TariffRequest;
import com.agentbanking.platform.transaction.TariffService.TariffResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Configuration", description = "Platform policy settings, tariff book, AI service status and demo reset")
public class AdminConfigController {

    private final SystemSettingService settingService;
    private final TariffService tariffService;
    private final AiServiceClient aiServiceClient;
    private final DemoResetService demoResetService;

    public record SettingUpdateRequest(@NotBlank @Size(max = 255) String value) {
    }

    public record DemoResetRequest(@NotBlank String confirm) {
    }

    @GetMapping("/settings")
    @Operation(summary = "All runtime policy settings (limits, recovery rules, credit caps, fraud thresholds)")
    public List<SettingResponse> settings() {
        return settingService.list();
    }

    @PutMapping("/settings/{key}")
    @Operation(summary = "Update a setting; values are validated and the change is audited")
    public SettingResponse updateSetting(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String key,
                                         @Valid @RequestBody SettingUpdateRequest request) {
        return settingService.update(key, request.value(), principal.getUsername());
    }

    @GetMapping("/tariffs")
    public List<TariffResponse> tariffs() {
        return tariffService.list();
    }

    @PostMapping("/tariffs")
    @ResponseStatus(HttpStatus.CREATED)
    public TariffResponse createTariff(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody TariffRequest request) {
        return tariffService.create(request, principal.getUsername());
    }

    @PutMapping("/tariffs/{id}")
    public TariffResponse updateTariff(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                       @Valid @RequestBody TariffRequest request) {
        return tariffService.update(id, request, principal.getUsername());
    }

    @GetMapping("/ai/status")
    @Operation(summary = "AI service configuration, reachability and which model source is currently active")
    public AiServiceClient.Status aiStatus() {
        return aiServiceClient.status();
    }

    @PostMapping("/demo/reset")
    @Operation(summary = "Erase all data and rebuild the demonstration dataset. Body must be {\"confirm\": \"RESET\"}")
    public ResetResult resetDemo(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody DemoResetRequest request) {
        if (!"RESET".equals(request.confirm())) {
            throw BusinessException.badRequest("CONFIRMATION_REQUIRED",
                    "Send {\"confirm\": \"RESET\"} to confirm the demo reset");
        }
        return demoResetService.reset(principal.getUsername());
    }
}
