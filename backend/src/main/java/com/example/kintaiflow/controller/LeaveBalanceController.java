package com.example.kintaiflow.controller;

import com.example.kintaiflow.dto.LeaveBalanceResponse;
import com.example.kintaiflow.service.LeaveBalanceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ONL-013 休暇残日数取得。対象は常にログイン本人（userId を受け取らない）。 */
@RestController
@RequestMapping("/api/leave-balances")
public class LeaveBalanceController {

    private final LeaveBalanceService leaveBalanceService;

    public LeaveBalanceController(LeaveBalanceService leaveBalanceService) {
        this.leaveBalanceService = leaveBalanceService;
    }

    @GetMapping
    public LeaveBalanceResponse getBalances(Authentication auth) {
        return leaveBalanceService.getBalances(Long.valueOf(auth.getName()));
    }
}
