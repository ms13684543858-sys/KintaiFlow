package com.example.kintaiflow.it;

import com.example.kintaiflow.entity.User;
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 打刻（出勤・退勤・休憩／離席）の結合テスト：状態遷移の不正を弾くことと、記録が本人にだけ見えること。 */
class AttendanceIT extends IntegrationTestBase {

    private static final String MONTH = YearMonth.now().toString();

    String token;

    @BeforeEach
    void setUp() throws Exception {
        token = login(createUser("EMPLOYEE", null));
    }

    @Test
    void clockInThenOutIsRecordedAndDuplicatesAreRejected() throws Exception {
        mvc.perform(authPost("/api/attendance/clock-in", token, null)).andExpect(status().isCreated());
        // 同じ日に 2 回出勤打刻はできない（E-006）
        mvc.perform(authPost("/api/attendance/clock-in", token, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-006"));

        mvc.perform(authPost("/api/attendance/clock-out", token, null)).andExpect(status().isOk());

        // 月次勤怠に今日の記録が出る
        MvcResult month = mvc.perform(authGet("/api/attendance?month=" + MONTH, token)).andExpect(status().isOk()).andReturn();
        JSONArray today = json(month, "$.records[?(@.workDate=='" + LocalDate.now() + "')]");
        assertEquals(1, today.size());
    }

    @Test
    void cannotClockOutOrTakeABreakBeforeClockingIn() throws Exception {
        mvc.perform(authPost("/api/attendance/clock-out", token, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-007"));
        mvc.perform(authPost("/api/attendance/break-start", token, "{\"kind\":\"BREAK\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-007"));
    }

    @Test
    void breakStateMachineRejectsInvalidTransitions() throws Exception {
        mvc.perform(authPost("/api/attendance/clock-in", token, null)).andExpect(status().isCreated());

        // 休憩していないのに終了はできない（E-021）
        mvc.perform(authPost("/api/attendance/break-end", token, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-021"));

        mvc.perform(authPost("/api/attendance/break-start", token, "{\"kind\":\"BREAK\"}")).andExpect(status().isCreated());
        // 休憩中に、もう一度休憩・離席を始めることはできない（E-020）
        mvc.perform(authPost("/api/attendance/break-start", token, "{\"kind\":\"AWAY\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-020"));
        // 休憩中は退勤できない（E-022）
        mvc.perform(authPost("/api/attendance/clock-out", token, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-022"));

        mvc.perform(authPost("/api/attendance/break-end", token, null)).andExpect(status().isOk());
        mvc.perform(authPost("/api/attendance/clock-out", token, null)).andExpect(status().isOk());
    }

    @Test
    void breakKindMustBeValid() throws Exception {
        mvc.perform(authPost("/api/attendance/clock-in", token, null)).andExpect(status().isCreated());
        mvc.perform(authPost("/api/attendance/break-start", token, "{\"kind\":\"NAP\"}")).andExpect(status().isBadRequest());
        mvc.perform(authPost("/api/attendance/break-start", token, "{}")).andExpect(status().isBadRequest());
    }

    @Test
    void attendanceIsVisibleOnlyToItsOwner() throws Exception {
        mvc.perform(authPost("/api/attendance/clock-in", token, null)).andExpect(status().isCreated());

        // 別の社員の月次勤怠には、他人の打刻は出ない
        String other = login(createUser("EMPLOYEE", null));
        MvcResult month = mvc.perform(authGet("/api/attendance?month=" + MONTH, other)).andExpect(status().isOk()).andReturn();
        assertEquals(0, ((JSONArray) json(month, "$.records[*]")).size());
    }

    @Test
    void afterClockingOutNothingMoreCanBeStamped() throws Exception {
        mvc.perform(authPost("/api/attendance/clock-in", token, null)).andExpect(status().isCreated());
        mvc.perform(authPost("/api/attendance/clock-out", token, null)).andExpect(status().isOk());
        // 退勤済みの日に、もう一度退勤はできない（E-006）
        mvc.perform(authPost("/api/attendance/clock-out", token, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-006"));
    }

    @Test
    void futureMonthCannotBeQueried() throws Exception {
        mvc.perform(authGet("/api/attendance?month=" + YearMonth.now().plusMonths(1), token)).andExpect(status().isBadRequest());
    }

    @Test
    void monthParameterIsValidated() throws Exception {
        mvc.perform(authGet("/api/attendance?month=2026-13", token)).andExpect(status().isBadRequest());
        mvc.perform(authGet("/api/attendance?month=abc", token)).andExpect(status().isBadRequest());
    }
}
