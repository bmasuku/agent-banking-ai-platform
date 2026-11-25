package com.agentbanking.platform.reporting;

import com.agentbanking.platform.reporting.ReportDtos.AdvanceBook;
import com.agentbanking.platform.reporting.ReportDtos.AreaReport;
import com.agentbanking.platform.reporting.ReportDtos.CommissionReport;
import com.agentbanking.platform.reporting.ReportDtos.FloatPosition;
import com.agentbanking.platform.reporting.ReportDtos.IncomeStatement;
import com.agentbanking.platform.reporting.ReportDtos.TransactionSummaryReport;
import com.agentbanking.platform.reporting.ReportDtos.TrialBalance;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE_OFFICER','ADMIN')")
@Tag(name = "Reports", description = "Ledger-derived financial reports. Periods default to the last 30 days.")
public class ReportController {

    private final ReportingService reportingService;

    @GetMapping("/trial-balance")
    @Operation(summary = "Trial balance across all ledger accounts (debits must equal credits)")
    public TrialBalance trialBalance() {
        return reportingService.trialBalance();
    }

    @GetMapping("/income-statement")
    @Operation(summary = "Fee income, interest income and commission expense for the period")
    public IncomeStatement incomeStatement(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportingService.incomeStatement(from, to);
    }

    @GetMapping("/float-position")
    @Operation(summary = "Network float position: agent e-float and cash, area pools, settlement bank and vault")
    public FloatPosition floatPosition() {
        return reportingService.floatPosition();
    }

    @GetMapping("/advance-book")
    @Operation(summary = "Float advance book: outstanding principal/interest, statuses and repayments by source")
    public AdvanceBook advanceBook(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportingService.advanceBook(from, to);
    }

    @GetMapping("/commissions")
    public CommissionReport commissions(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportingService.commissionReport(from, to);
    }

    @GetMapping("/transactions")
    public TransactionSummaryReport transactions(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                 @RequestParam(required = false) UUID areaId) {
        return reportingService.transactionSummary(from, to, areaId);
    }

    @GetMapping("/areas/{areaId}")
    public AreaReport area(@PathVariable UUID areaId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportingService.areaReport(areaId, from, to);
    }
}
