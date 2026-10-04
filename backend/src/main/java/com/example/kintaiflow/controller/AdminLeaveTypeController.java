package com.example.kintaiflow.controller;

import com.example.kintaiflow.dto.*;
import com.example.kintaiflow.service.LeaveTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** ONL-015 休暇種別管理 API（ADMIN 限定）。 */
@RestController
@RequestMapping("/api/admin/leave-types")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    public AdminLeaveTypeController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }

    @GetMapping
    public LeaveTypeListResponse list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return leaveTypeService.list(activeOnly);
    }

    @GetMapping("/{id}")
    public LeaveTypeDetailResponse get(@PathVariable Long id) {
        return leaveTypeService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveTypeDetailResponse create(@Valid @RequestBody LeaveTypeRequest request) {
        return leaveTypeService.create(request);
    }

    @PutMapping("/{id}")
    public LeaveTypeDetailResponse update(@PathVariable Long id, @Valid @RequestBody LeaveTypeRequest request) {
        return leaveTypeService.update(id, request);
    }
}
