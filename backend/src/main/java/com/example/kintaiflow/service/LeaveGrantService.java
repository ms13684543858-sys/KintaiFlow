package com.example.kintaiflow.service;

import com.example.kintaiflow.batch.BatchResult;
import com.example.kintaiflow.dto.GrantCandidate;
import com.example.kintaiflow.entity.LeaveType;
import com.example.kintaiflow.repository.LeaveBalanceRepository;
import com.example.kintaiflow.repository.LeaveTypeRepository;
import com.example.kintaiflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * BAT-001 年次有給休暇 自動付与の本体。トランザクションは張らない（1件ごとに LeaveGrantUnitService が REQUIRES_NEW）。
 * 1件の失敗で止めず、件数を集計して返す。
 */
@Service
public class LeaveGrantService {

    private static final Logger log = LoggerFactory.getLogger(LeaveGrantService.class);

    private final LeaveTypeRepository leaveTypeRepository;
    private final UserRepository userRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveGrantUnitService unitService;
    private final int catchUpDays;

    public LeaveGrantService(LeaveTypeRepository leaveTypeRepository, UserRepository userRepository,
                             LeaveBalanceRepository leaveBalanceRepository, LeaveGrantUnitService unitService,
                             @Value("${kintaiflow.batch.leave-grant.catch-up-days:7}") int catchUpDays) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.userRepository = userRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.unitService = unitService;
        this.catchUpDays = catchUpDays;
    }

    public int catchUpDays() { return catchUpDays; }

    /** @throws IllegalStateException 年休種別が無い／無効（BAT001-E01） */
    public BatchResult execute(LocalDate today) {
        long t0 = System.nanoTime();
        LeaveType type = leaveTypeRepository.findByName(LeaveBalanceService.ANNUAL_LEAVE_NAME)
                .filter(t -> Boolean.TRUE.equals(t.getIsActive()) && "LIMITED".equals(t.getMaxDaysRule()))
                .orElseThrow(() -> new IllegalStateException("annual leave type unavailable"));
        LocalDate from = today.minusDays(catchUpDays);   // 取りこぼし救済（冪等なので重複しない）
        List<GrantCandidate> users = userRepository.findGrantCandidates(today.minusMonths(5));

        int targets = 0, granted = 0, skipped = 0, failed = 0;
        for (GrantCandidate u : users) {
            for (LeaveGrantCalculator.GrantDue due : LeaveGrantCalculator.dueDates(u.hireDate(), from, today)) {
                targets++;
                try {
                    switch (unitService.grantOne(u.id(), type.getId(), type.getName(), due.grantDate())) {
                        case GRANTED -> granted++;
                        case SKIPPED_EXISTS -> skipped++;
                        case SKIPPED_NOT_TARGET -> {
                            skipped++;
                            log.warn("BAT-001 skipped userId={} grantedOn={} reason=not-target", u.id(), due.grantDate());
                        }
                    }
                } catch (DataIntegrityViolationException e) {
                    // 事前確認をすり抜けた同時実行（uq_leave_balances_1）。Tx の外で再確認し、既付与なら SKIP
                    if (leaveBalanceRepository.existsByUserIdAndLeaveTypeIdAndGrantedOn(
                            u.id(), type.getId(), due.grantDate())) {
                        skipped++;
                        log.warn("BAT-001 skipped userId={} grantedOn={} reason=duplicate", u.id(), due.grantDate());
                    } else {
                        failed++;
                        log.error("BAT-001 item failed userId={} grantedOn={} (integrity violation)",
                                u.id(), due.grantDate(), e);
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("BAT-001 item failed userId={} grantedOn={}", u.id(), due.grantDate(), e);
                }
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        return new BatchResult("BAT-001", today, users.size(), targets, granted, skipped, failed, ms, "");
    }
}
