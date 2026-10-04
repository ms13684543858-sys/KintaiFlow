package com.example.kintaiflow.controller;

import com.example.kintaiflow.dto.LeaveTypeOptionListResponse;
import com.example.kintaiflow.service.LeaveTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ONL-021 有効休暇種別一覧取得（全ロール可・認証必須）。 */
@RestController
@RequestMapping("/api/leave-types")
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    public LeaveTypeController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }

    @GetMapping
    public LeaveTypeOptionListResponse list() {
        return leaveTypeService.listActiveOptions();
    }
}
