package com.agentbanking.platform.area;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public final class AreaDtos {

    private AreaDtos() {
    }

    public record AreaResponse(UUID id, String code, String name, String province,
                               BigDecimal centerLatitude, BigDecimal centerLongitude,
                               long agentCount, String managerName,
                               BigDecimal efloatPoolBalance, BigDecimal cashPoolBalance) {
    }

    public record CreateAreaRequest(
            @NotBlank @Size(max = 32) @Pattern(regexp = "^[A-Z0-9-]+$") String code,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 80) String province,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal centerLatitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal centerLongitude) {
    }
}
