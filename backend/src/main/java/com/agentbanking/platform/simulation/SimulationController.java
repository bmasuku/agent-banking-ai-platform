package com.agentbanking.platform.simulation;

import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.security.UserPrincipal;
import com.agentbanking.platform.simulation.SimulationService.ScenarioInfo;
import com.agentbanking.platform.simulation.SimulationService.SimulationRequest;
import com.agentbanking.platform.simulation.SimulationService.SimulationRunResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/simulations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Simulations", description = "Generate simulated market activity through the real transaction pipeline")
public class SimulationController {

    private final SimulationService simulationService;

    @GetMapping("/scenarios")
    public List<ScenarioInfo> scenarios() {
        return simulationService.scenarios();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Run a simulation scenario synchronously and return its results")
    public SimulationRunResponse run(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody SimulationRequest request) {
        return simulationService.run(request, principal.getId());
    }

    @GetMapping
    public PageResponse<SimulationRunResponse> list(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return simulationService.list(PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
    }

    @GetMapping("/{id}")
    public SimulationRunResponse get(@PathVariable UUID id) {
        return simulationService.get(id);
    }
}
