package com.example.kintaiflow.controller;

import com.example.kintaiflow.dto.HolidayDetailResponse;
import com.example.kintaiflow.dto.HolidayListResponse;
import com.example.kintaiflow.dto.HolidayRequest;
import com.example.kintaiflow.service.HolidayService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** ONL-016 祝日管理 API（ADMIN 限定）。 */
@RestController
@RequestMapping("/api/admin/holidays")
@PreAuthorize("hasRole('ADMIN')")
public class AdminHolidayController {

    private final HolidayService holidayService;

    public AdminHolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @GetMapping
    public HolidayListResponse list(@RequestParam(required = false) Integer year) {
        return holidayService.list(year);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayDetailResponse create(@Valid @RequestBody HolidayRequest request) {
        return holidayService.create(request);
    }

    @DeleteMapping("/{id}")
    public HolidayDetailResponse delete(@PathVariable Long id) {
        return holidayService.delete(id);
    }
}
