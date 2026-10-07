package com.example.kintaiflow.controller;

import com.example.kintaiflow.dto.*;
import com.example.kintaiflow.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** API-002 出勤打刻 / API-003 退勤打刻 / API-004 月次勤怠取得。対象は常にログイン本人。 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/clock-in")
    @ResponseStatus(HttpStatus.CREATED)
    public ClockInResponse clockIn(Authentication auth) {
        return attendanceService.clockIn(Long.valueOf(auth.getName()));
    }

    @PostMapping("/clock-out")
    public ClockOutResponse clockOut(Authentication auth) {
        return attendanceService.clockOut(Long.valueOf(auth.getName()));
    }

    /** 休憩・日中離席の開始（kind=BREAK|AWAY）。 */
    @PostMapping("/break-start")
    @ResponseStatus(HttpStatus.CREATED)
    public BreakResponse breakStart(@Valid @RequestBody BreakStartRequest request, Authentication auth) {
        return attendanceService.startBreak(Long.valueOf(auth.getName()), request.kind());
    }

    /** 継続中の休憩・日中離席の終了。 */
    @PostMapping("/break-end")
    public BreakResponse breakEnd(Authentication auth) {
        return attendanceService.endBreak(Long.valueOf(auth.getName()));
    }

    @GetMapping
    public MonthlyAttendanceResponse getMonthly(@RequestParam(name = "month", required = false) String month,
                                                Authentication auth) {
        return attendanceService.getMonthly(Long.valueOf(auth.getName()), month);
    }
}
