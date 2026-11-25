package com.agentbanking.platform.area;

import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.area.AreaDtos.AreaResponse;
import com.agentbanking.platform.area.AreaDtos.CreateAreaRequest;
import com.agentbanking.platform.audit.AuditService;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.ledger.BalanceType;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.user.Role;
import com.agentbanking.platform.user.User;
import com.agentbanking.platform.user.UserRepository;
import com.agentbanking.platform.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AreaService {

    private final AreaRepository areaRepository;
    private final AgentRepository agentRepository;
    private final UserRepository userRepository;
    private final LedgerAccountService ledgerAccountService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AreaResponse> list() {
        return areaRepository.findAllByOrderByName().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AreaResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public AreaResponse create(CreateAreaRequest request) {
        if (areaRepository.existsByCode(request.code())) {
            throw BusinessException.conflict("AREA_CODE_TAKEN", "Area code already exists: " + request.code());
        }
        Area area = new Area();
        area.setCode(request.code());
        area.setName(request.name());
        area.setProvince(request.province());
        area.setCenterLatitude(request.centerLatitude());
        area.setCenterLongitude(request.centerLongitude());
        areaRepository.save(area);
        ledgerAccountService.provisionAreaAccounts(area);
        auditService.record("AREA_CREATED", "AREA", area.getId(), Map.of("code", area.getCode()));
        return toResponse(area);
    }

    @Transactional(readOnly = true)
    public Area require(UUID id) {
        return areaRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Area", id));
    }

    private AreaResponse toResponse(Area area) {
        String managers = userRepository.findByRoleAndArea_IdAndStatus(Role.AREA_MANAGER, area.getId(), UserStatus.ACTIVE)
                .stream().map(User::getFullName).collect(Collectors.joining(", "));
        return new AreaResponse(area.getId(), area.getCode(), area.getName(), area.getProvince(),
                area.getCenterLatitude(), area.getCenterLongitude(),
                agentRepository.countByArea_Id(area.getId()),
                managers.isEmpty() ? null : managers,
                ledgerAccountService.areaBalance(area.getId(), BalanceType.AREA_EFLOAT_POOL),
                ledgerAccountService.areaBalance(area.getId(), BalanceType.AREA_CASH_POOL));
    }
}
