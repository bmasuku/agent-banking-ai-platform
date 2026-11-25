package com.agentbanking.platform.audit;

import com.agentbanking.platform.audit.AuditService.AuditLogResponse;
import com.agentbanking.platform.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasAnyRole('ADMIN','RISK_OFFICER')")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Immutable audit trail")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Search the immutable audit trail")
    public PageResponse<AuditLogResponse> search(@RequestParam(required = false) String action,
                                                 @RequestParam(required = false) String entityType,
                                                 @RequestParam(required = false) String entityId,
                                                 @RequestParam(required = false) UUID actorId,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int size) {
        return auditService.search(action, entityType, entityId, actorId, from, to,
                PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
