package com.example.kintaiflow.controller;

import com.example.kintaiflow.dto.MonthlyReportResponse;
import com.example.kintaiflow.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;

/** ONL-018 月次集計取得（API-018）。管理者のみ。format=csv で RPT-001 をダウンロード用に返す。 */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private static final Logger log = LoggerFactory.getLogger(AdminReportController.class);

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/monthly")
    public ResponseEntity<?> monthly(@RequestParam(name = "month", required = false) String month,
                                     @RequestParam(name = "departmentId", required = false) Long departmentId,
                                     @RequestParam(name = "format", required = false, defaultValue = "json") String format,
                                     Authentication auth) {
        YearMonth ym = reportService.parseMonth(month);
        boolean csv = reportService.parseFormat(format);
        MonthlyReportResponse res = reportService.monthly(ym, departmentId);
        if (!csv) return ResponseEntity.ok(res);
        byte[] body = reportService.toCsv(res);
        log.info("Monthly report exported month={} departmentId={} rows={} by={}",
                ym, departmentId, res.rows().size(), Long.valueOf(auth.getName()));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(reportService.csvFileName(ym)).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
