package com.example.kintaiflow.controller;

import com.example.kintaiflow.dto.LeaveGrantAdjustRequest;
import com.example.kintaiflow.dto.LeaveGrantCreateRequest;
import com.example.kintaiflow.dto.LeaveGrantListResponse;
import com.example.kintaiflow.dto.LeaveGrantResponse;
import com.example.kintaiflow.service.LeaveBalanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** ONL-017 休暇付与（管理者のみ）。付与履歴参照・付与追加・付与日数調整。 */
@RestController
@RequestMapping("/api/admin/leave-grants")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLeaveGrantController {

    private final LeaveBalanceService leaveBalanceService;

    public AdminLeaveGrantController(LeaveBalanceService leaveBalanceService) {
        this.leaveBalanceService = leaveBalanceService;
    }

    @GetMapping
    public LeaveGrantListResponse list(@RequestParam(name = "userId", required = false) Long userId,
                                       @RequestParam(name = "leaveTypeId", required = false) Long leaveTypeId) {
        return leaveBalanceService.listGrants(userId, leaveTypeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveGrantResponse create(@Valid @RequestBody LeaveGrantCreateRequest request, Authentication auth) {
        return leaveBalanceService.grantManually(request, Long.valueOf(auth.getName()));
    }

    @PutMapping("/{id}")
    public LeaveGrantResponse adjust(@PathVariable("id") Long id,
                                     @Valid @RequestBody LeaveGrantAdjustRequest request, Authentication auth) {
        return leaveBalanceService.adjust(id, request, Long.valueOf(auth.getName()));
    }
}
