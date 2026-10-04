package com.example.kintaiflow.controller;

import com.example.kintaiflow.dto.SubordinateSummaryResponse;
import com.example.kintaiflow.service.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ONL-020 上長の部下一覧（API-020）。上長 ID は JWT から取得し、引数では受け取らない。 */
@RestController
@RequestMapping("/api/manager")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerController {

    private final ReportService reportService;

    public ManagerController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/subordinates/summary")
    public SubordinateSummaryResponse subordinatesSummary(@RequestParam(name = "month", required = false) String month,
                                                          Authentication auth) {
        return reportService.subordinates(Long.valueOf(auth.getName()), month);
    }
}
