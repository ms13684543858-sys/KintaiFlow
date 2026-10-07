package com.example.kintaiflow.service;

import com.example.kintaiflow.entity.LeaveBalance;
import com.example.kintaiflow.entity.NotificationType;
import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.repository.LeaveBalanceRepository;
import com.example.kintaiflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.OptionalInt;

/** BAT-001 ユーザー1件分の付与。LeaveGrantService とは別 Bean（REQUIRES_NEW のプロキシを効かせるため）。 */
@Service
public class LeaveGrantUnitService {

    public enum GrantOutcome { GRANTED, SKIPPED_EXISTS, SKIPPED_NOT_TARGET }

    private static final Logger log = LoggerFactory.getLogger(LeaveGrantUnitService.class);

    private final UserRepository userRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final NotificationService notificationService;

    public LeaveGrantUnitService(UserRepository userRepository, LeaveBalanceRepository leaveBalanceRepository,
                                 LeaveBalanceService leaveBalanceService, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.notificationService = notificationService;
    }

    /** ユーザー行をロックして再判定し、未付与なら付与行の INSERT と本人への通知を同一 Tx で行う。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GrantOutcome grantOne(Long userId, Long leaveTypeId, String leaveTypeName, LocalDate grantedOn) {
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null || !"ACTIVE".equals(user.getStatus())) return GrantOutcome.SKIPPED_NOT_TARGET;
        OptionalInt idx = LeaveGrantCalculator.indexOf(user.getHireDate(), grantedOn);   // 入社日修正への備え
        if (idx.isEmpty()) return GrantOutcome.SKIPPED_NOT_TARGET;
        if (leaveBalanceRepository.existsByUserIdAndLeaveTypeIdAndGrantedOn(userId, leaveTypeId, grantedOn))
            return GrantOutcome.SKIPPED_EXISTS;   // 手動付与済みも含む（冪等）

        BigDecimal days = LeaveGrantCalculator.grantDays(idx.getAsInt());
        LeaveBalance lb = leaveBalanceService.grant(userId, leaveTypeId, grantedOn, days);
        String note = "勤続" + LeaveGrantCalculator.label(idx.getAsInt()) + "の法定付与";
        notificationService.notify(userId, NotificationType.LEAVE_GRANTED,
                leaveBalanceService.buildGrantMessage(leaveTypeName, lb, note), null);
        log.info("BAT-001 granted userId={} grantedOn={} days={} expiresOn={}",
                userId, grantedOn, lb.getGrantedDays(), lb.getExpiresOn());
        return GrantOutcome.GRANTED;
    }
}
