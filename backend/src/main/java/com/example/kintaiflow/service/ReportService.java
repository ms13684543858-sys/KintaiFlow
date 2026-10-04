package com.example.kintaiflow.service;

import com.example.kintaiflow.dto.MonthlyReportResponse;
import com.example.kintaiflow.dto.MonthlyReportRow;
import com.example.kintaiflow.dto.SubordinateSummaryResponse;
import com.example.kintaiflow.exception.BusinessException;
import com.example.kintaiflow.repository.ReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** ONL-018 月次集計取得（管理者）/ ONL-020 上長の部下一覧 の集計・CSV 生成。 */
@Service
public class ReportService {

    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /** month（yyyy-MM）を検証して YearMonth に変換する。 */
    public YearMonth parseMonth(String month) {
        if (month == null || month.isBlank())
            throw new BusinessException("E-002", "対象月は必須入力です。", HttpStatus.BAD_REQUEST);
        if (!month.trim().matches("^\\d{4}-(0[1-9]|1[0-2])$"))
            throw new BusinessException("E-002", "対象月はyyyy-MM形式で入力してください。", HttpStatus.BAD_REQUEST);
        return YearMonth.parse(month.trim());
    }

    /** format を検証する。csv なら true。 */
    public boolean parseFormat(String format) {
        if (format == null || format.isBlank()) return false;
        switch (format.trim().toLowerCase(Locale.ROOT)) {
            case "json":
                return false;
            case "csv":
                return true;
            default:
                throw new BusinessException("E-002", "出力形式はjsonまたはcsvで指定してください。", HttpStatus.BAD_REQUEST);
        }
    }

    /** 全社（または部署）の月次集計。 */
    @Transactional(readOnly = true)
    public MonthlyReportResponse monthly(YearMonth ym, Long departmentId) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<Object[]> raw = reportRepository.findMonthlySummary(from, to, departmentId, null);
        return new MonthlyReportResponse(ym.toString(), departmentId, toRows(raw));
    }

    /** 直属の部下の月次集計（managerId は null 不可。null だと全社集計になるため）。 */
    @Transactional(readOnly = true)
    public SubordinateSummaryResponse subordinates(Long managerId, String month) {
        Objects.requireNonNull(managerId, "managerId");
        YearMonth ym = parseMonth(month);
        List<Object[]> raw = reportRepository.findMonthlySummary(ym.atDay(1), ym.atEndOfMonth(), null, managerId);
        return new SubordinateSummaryResponse(ym.toString(), toRows(raw));
    }

    /** native クエリ結果を DTO に変換する（列順は findMonthlySummary の SELECT と一致）。 */
    static List<MonthlyReportRow> toRows(List<Object[]> raw) {
        List<MonthlyReportRow> list = new ArrayList<>();
        for (Object[] o : raw) {
            Long userId = ((Number) o[0]).longValue();
            String name = (String) o[1];
            String dept = (String) o[2];
            long days = ((Number) o[3]).longValue();
            long work = ((Number) o[4]).longValue();
            long overtime = ((Number) o[5]).longValue();
            BigDecimal leave = (o[6] instanceof BigDecimal b) ? b : new BigDecimal(o[6].toString());
            list.add(new MonthlyReportRow(userId, name, dept, days, work, overtime,
                    leave.setScale(1, RoundingMode.HALF_UP)));
        }
        return list;
    }

    /** RPT-001 の CSV（UTF-8 BOM 付き・CRLF）を生成する。 */
    public byte[] toCsv(MonthlyReportResponse res) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("社員ID,氏名,部署,出勤日数,総勤務時間,残業時間,休暇取得日数\r\n");
        for (MonthlyReportRow r : res.rows()) {
            sb.append(r.userId()).append(',')
                    .append(csvCell(r.userName())).append(',')
                    .append(csvCell(r.departmentName())).append(',')
                    .append(r.workDays()).append(',')
                    .append(formatHm(r.workMinutes())).append(',')
                    .append(formatHm(r.overtimeMinutes())).append(',')
                    .append(r.leaveDays().setScale(1, RoundingMode.HALF_UP).toPlainString())
                    .append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public String csvFileName(YearMonth ym) {
        return "kintai_summary_" + ym.format(DateTimeFormatter.ofPattern("yyyyMM")) + ".csv";
    }

    /** CSV セルのエスケープ（数式インジェクション対策込み）。 */
    static String csvCell(String v) {
        if (v == null) return "";
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r"))
            return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }

    /** 分を H:mm に変換する（6930 → "115:30"）。 */
    static String formatHm(long minutes) {
        long x = Math.max(0, minutes);
        return (x / 60) + ":" + String.format(Locale.ROOT, "%02d", x % 60);
    }
}
