package com.example.kintaiflow.config;

import com.example.kintaiflow.entity.LeaveType;
import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.repository.LeaveBalanceRepository;
import com.example.kintaiflow.repository.LeaveTypeRepository;
import com.example.kintaiflow.repository.UserRepository;
import com.example.kintaiflow.service.LeaveBalanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * 開発用：leave_balances が空のときだけ、全ユーザーに年次有給休暇 20 日（当年度 4/1 付与）を入れる。
 * DevDataInitializer と同じく kintaiflow.dev.seed-password が設定されているときだけ動く（本番では動かない）。
 */
@Component
@Order(3)
public class DevLeaveBalanceInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevLeaveBalanceInitializer.class);

    private final UserRepository userRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final Clock clock;
    private final String seedPassword;

    public DevLeaveBalanceInitializer(UserRepository userRepository, LeaveTypeRepository leaveTypeRepository,
                                      LeaveBalanceRepository leaveBalanceRepository,
                                      LeaveBalanceService leaveBalanceService, Clock clock,
                                      @Value("${kintaiflow.dev.seed-password:}") String seedPassword) {
        this.userRepository = userRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.clock = clock;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(String... args) {
        if (seedPassword.isBlank() || leaveBalanceRepository.count() > 0 || userRepository.count() == 0) {
            return;
        }
        LeaveType annual = leaveTypeRepository.findByName("年次有給休暇").orElse(null);
        if (annual == null) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate grantedOn = LocalDate.of(today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1, 4, 1);
        int n = 0;
        for (User u : userRepository.findAll()) {
            if (!"ACTIVE".equals(u.getStatus())) continue;
            leaveBalanceService.grant(u.getId(), annual.getId(), grantedOn, new BigDecimal("20.0"));
            n++;
        }
        log.info("Dev seed: granted annual leave 20 days x {} users (grantedOn={})", n, grantedOn);
    }
}
