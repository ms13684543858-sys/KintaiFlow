package com.example.kintaiflow.service;

import com.example.kintaiflow.batch.BatchResult;
import com.example.kintaiflow.dto.AlertBaseRow;
import com.example.kintaiflow.entity.LeaveBalance;
import com.example.kintaiflow.entity.LeaveType;
import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.repository.LeaveTypeRepository;
import com.example.kintaiflow.repository.RequestRepository;
import com.example.kintaiflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BAT-002 年5日取得義務アラートの本体。年10日以上付与され、付与から9か月以上経過しても承認済みの取得が5日未満の者を、
 * 本人と全管理者へ通知する。トランザクションは張らない（対象者ごとに MandatoryLeaveAlertUnitService が REQUIRES_NEW）。
 */
@Service
public class MandatoryLeaveAlertService {

    private static final Logger log = LoggerFactory.getLogger(MandatoryLeaveAlertService.class);

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final MandatoryLeaveAlertUnitService unitService;
    private final Clock clock;
    private final BigDecimal threshold;
    private final int elapsedMonths;

    public MandatoryLeaveAlertService(LeaveTypeRepository leaveTypeRepository, LeaveBalanceService leaveBalanceService,
                                      RequestRepository requestRepository, UserRepository userRepository,
                                      MandatoryLeaveAlertUnitService unitService, Clock clock,
                                      @Value("${kintaiflow.batch.mandatory-alert.threshold-days:5.0}") BigDecimal threshold,
                                      @Value("${kintaiflow.batch.mandatory-alert.elapsed-months:9}") int elapsedMonths) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.unitService = unitService;
        this.clock = clock;
        this.threshold = threshold;
        this.elapsedMonths = elapsedMonths;
    }

    /** @throws IllegalStateException 年休種別が無い／無効（BAT002-E01） */
    public BatchResult execute(LocalDate today) {
        long t0 = System.nanoTime();
        LeaveType type = leaveTypeRepository.findByName(LeaveBalanceService.ANNUAL_LEAVE_NAME)
                .filter(t -> Boolean.TRUE.equals(t.getIsActive()) && "LIMITED".equals(t.getMaxDaysRule()))
                .orElseThrow(() -> new IllegalStateException("annual leave type unavailable"));
        YearMonth month = YearMonth.now(clock);   // 同月判定は実行時点の暦月（target-date は期間判定のみ）

        // ユーザーごとに granted_on が最新の1行を基準行にする（findAlertBaseRows は user_id, granted_on DESC 順）
        Map<Long, LeaveBalance> latest = new LinkedHashMap<>();
        for (LeaveBalance b : leaveBalanceService.findAlertBaseRows(today)) latest.putIfAbsent(b.getUserId(), b);

        List<Long> adminIds = userRepository.findByRoleAndStatus("ADMIN", "ACTIVE").stream().map(User::getId).toList();
        if (adminIds.isEmpty()) log.warn("BAT-002 no active admin; notifying applicants only");
        Map<Long, String> names = userRepository.findAllById(latest.keySet()).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

        int targets = 0, succeeded = 0, skipped = 0, failed = 0, compliant = 0, notifications = 0;
        for (LeaveBalance b : latest.values()) {
            if (b.getGrantedOn().plusMonths(elapsedMonths).isAfter(today)) continue;   // 9か月未満は対象外
            BigDecimal taken = requestRepository.sumApprovedLeaveDays(b.getUserId(), type.getId(),
                    b.getGrantedOn(), MandatoryLeaveMessages.deadline(b.getGrantedOn()));
            if (taken == null) taken = BigDecimal.ZERO.setScale(1);
            if (taken.compareTo(threshold) >= 0) { compliant++; continue; }   // 義務達成済み

            targets++;
            AlertBaseRow row = AlertBaseRow.of(b, names.getOrDefault(b.getUserId(), ""));
            try {
                MandatoryLeaveAlertUnitService.AlertOutcome o = unitService.alertOne(row, taken, threshold, adminIds, month);
                notifications += o.created();
                if (o.created() > 0) succeeded++; else skipped++;   // 全宛先が同月に送信済みなら SKIP
            } catch (Exception e) {
                failed++;
                log.error("BAT-002 item failed userId={} grantedOn={}", b.getUserId(), b.getGrantedOn(), e);
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        return new BatchResult("BAT-002", today, latest.size(), targets, succeeded, skipped, failed, ms,
                "compliant=" + compliant + " notifications=" + notifications);
    }
}
