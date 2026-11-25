package com.agentbanking.platform.area;

import com.agentbanking.platform.area.AreaDtos.AreaResponse;
import com.agentbanking.platform.area.AreaDtos.CreateAreaRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Geographical Areas")
public class AreaController {

    private final AreaService areaService;

    @GetMapping("/api/v1/areas")
    @PreAuthorize("hasAnyRole('RISK_OFFICER','FINANCE_OFFICER','ADMIN')")
    @Operation(summary = "List all geographical areas with pool balances")
    public List<AreaResponse> list() {
        return areaService.list();
    }

    @GetMapping("/api/v1/areas/{id}")
    @PreAuthorize("hasAnyRole('RISK_OFFICER','FINANCE_OFFICER','ADMIN')")
    public AreaResponse get(@PathVariable UUID id) {
        return areaService.get(id);
    }

    @PostMapping("/api/v1/admin/areas")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an area and provision its e-float and cash pool ledger accounts")
    public AreaResponse create(@Valid @RequestBody CreateAreaRequest request) {
        return areaService.create(request);
    }
}
