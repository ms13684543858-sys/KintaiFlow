package com.example.kintaiflow.it;

import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.service.HolidayService;
import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 休暇申請 → 上長の承認 → 管理者の最終承認 → 残日数の更新、までの業務フローと、その権限・不正系の結合テスト。
 * 画面ではなく HTTP API を、本物のセキュリティ設定・DB に対して呼ぶ。
 */
class LeaveApprovalFlowIT extends IntegrationTestBase {

    @Autowired HolidayService holidayService;

    User manager, employee, admin;
    String managerToken, employeeToken, adminToken;
    LocalDate day;

    @BeforeEach
    void setUp() throws Exception {
        manager = createUser("MANAGER", null);
        employee = createUser("EMPLOYEE", manager);
        admin = createUser("ADMIN", null);
        grantAnnualLeave(employee, "20.0");
        managerToken = login(manager);
        employeeToken = login(employee);
        adminToken = login(admin);
        day = nextBusinessDay(LocalDate.now().plusMonths(2));
    }

    // ---------------------------------------------------------------- 正常系：2 段階承認

    @Test
    void leaveRequestGoesThroughManagerThenAdminAndConsumesBalance() throws Exception {
        assertEquals(20.0, remainingDays(employeeToken), 0.001);

        long requestId = createLeave(employeeToken, day, day, "FULL");

        // 上長の承認待ちに載る（1 段目）。社員本人・管理者の最終承認待ちにはまだ載らない
        assertEquals(1, pendingStepNo(managerToken, requestId));
        assertEquals(0, pendingCount(adminToken, requestId));
        mvc.perform(authGet("/api/approvals/pending", employeeToken)).andExpect(status().isForbidden());

        // 上長が承認 → まだ承認待ち（管理者の最終承認待ち）。残日数は減らない
        mvc.perform(authPost("/api/approvals/" + stepIdFor(managerToken, requestId) + "/approve", managerToken, "{\"comment\":\"OK\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestStatus").value("PENDING"));
        assertEquals(0, pendingCount(managerToken, requestId));
        assertEquals(2, pendingStepNo(adminToken, requestId));
        assertEquals(20.0, remainingDays(employeeToken), 0.001);

        // 管理者が最終承認 → 承認済み。残日数が 1 日減る
        mvc.perform(authPost("/api/approvals/" + stepIdFor(adminToken, requestId) + "/approve", adminToken, "{\"comment\":\"OK\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestStatus").value("APPROVED"));
        assertEquals(19.0, remainingDays(employeeToken), 0.001);
        mvc.perform(authGet("/api/requests/" + requestId, employeeToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.request.status").value("APPROVED"));

        // 申請者へ「承認された」通知が届く
        MvcResult notes = mvc.perform(authGet("/api/notifications", employeeToken)).andExpect(status().isOk()).andReturn();
        JSONArray types = json(notes, "$.notifications[*].type");
        assertTrue(types.contains("REQUEST_APPROVED"), "承認の通知が届いていない: " + types);
    }

    // ---------------------------------------------------------------- 差戻し・取下げ

    @Test
    void returningRequiresACommentAndTheApplicantCanThenWithdraw() throws Exception {
        long requestId = createLeave(employeeToken, day, day, "FULL");
        long stepId = stepIdFor(managerToken, requestId);

        // 差戻しはコメント必須（E-008）
        mvc.perform(authPost("/api/approvals/" + stepId + "/return", managerToken, "{\"comment\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-008"));
        mvc.perform(authPost("/api/approvals/" + stepId + "/return", managerToken, "{\"comment\":\"日付を見直してください\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestStatus").value("RETURNED"));

        // 残日数は変わらない。差戻しされた申請は、本人が取り下げられる
        assertEquals(20.0, remainingDays(employeeToken), 0.001);
        mvc.perform(authPost("/api/requests/" + requestId + "/withdraw", employeeToken, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void rejectingNeedsACommentAndKeepsTheBalance() throws Exception {
        long requestId = createLeave(employeeToken, day, day, "FULL");
        long stepId = stepIdFor(managerToken, requestId);
        mvc.perform(authPost("/api/approvals/" + stepId + "/reject", managerToken, "{\"comment\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-008"));
        mvc.perform(authPost("/api/approvals/" + stepId + "/reject", managerToken, "{\"comment\":\"繁忙期のため\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestStatus").value("REJECTED"));
        assertEquals(20.0, remainingDays(employeeToken), 0.001);
    }

    // ---------------------------------------------------------------- 権限（他人の申請・他人の承認）

    @Test
    void anotherManagerCannotApproveSomeoneElsesRequest() throws Exception {
        long requestId = createLeave(employeeToken, day, day, "FULL");
        long stepId = stepIdFor(managerToken, requestId);

        String otherManagerToken = login(createUser("MANAGER", null));
        assertEquals(0, pendingCount(otherManagerToken, requestId));   // 一覧にも出ない
        mvc.perform(authPost("/api/approvals/" + stepId + "/approve", otherManagerToken, "{\"comment\":\"勝手に承認\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("E-010"));
    }

    @Test
    void aManagerCannotDoTheAdminsFinalApproval() throws Exception {
        long requestId = createLeave(employeeToken, day, day, "FULL");
        mvc.perform(authPost("/api/approvals/" + stepIdFor(managerToken, requestId) + "/approve", managerToken, "{}"))
                .andExpect(status().isOk());
        long finalStepId = stepIdFor(adminToken, requestId);

        // 1 段目を承認した上長でも、2 段目（管理者の最終承認）はできない
        mvc.perform(authPost("/api/approvals/" + finalStepId + "/approve", managerToken, "{\"comment\":\"自分で最終承認\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(authPost("/api/approvals/" + finalStepId + "/approve", employeeToken, "{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestsAreVisibleOnlyToTheApplicantTheApproversAndAdmins() throws Exception {
        long requestId = createLeave(employeeToken, day, day, "FULL");
        String url = "/api/requests/" + requestId;

        mvc.perform(authGet(url, employeeToken)).andExpect(status().isOk());        // 本人
        mvc.perform(authGet(url, managerToken)).andExpect(status().isOk());         // 承認者（上長）
        mvc.perform(authGet(url, adminToken)).andExpect(status().isOk());           // 管理者
        String otherEmployeeToken = login(createUser("EMPLOYEE", null));
        String otherManagerToken = login(createUser("MANAGER", null));
        mvc.perform(authGet(url, otherEmployeeToken)).andExpect(status().isForbidden());   // 無関係の社員
        mvc.perform(authGet(url, otherManagerToken)).andExpect(status().isForbidden());    // 無関係の上長

        // 他人の申請を取り下げることもできない
        mvc.perform(authPost(url + "/withdraw", otherEmployeeToken, null)).andExpect(status().isForbidden());
        // 申請一覧にも他人の申請は出ない
        MvcResult list = mvc.perform(authGet("/api/requests", otherEmployeeToken)).andExpect(status().isOk()).andReturn();
        assertEquals(0, ((JSONArray) json(list, "$.requests[?(@.requestId==" + requestId + ")]")).size());
    }

    // ---------------------------------------------------------------- 入力・業務ルール

    @Test
    void overlappingLeaveForTheSameUserIsRejected() throws Exception {
        createLeave(employeeToken, day, day, "FULL");
        mvc.perform(authPost("/api/requests", employeeToken, leaveJson(day, day, "FULL")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-005"));
    }

    @Test
    void leaveBeyondTheRemainingBalanceIsRejected() throws Exception {
        User poor = createUser("EMPLOYEE", manager);
        grantAnnualLeave(poor, "1.0");
        String poorToken = login(poor);
        LocalDate next = nextBusinessDay(day.plusDays(1));
        mvc.perform(authPost("/api/requests", poorToken, leaveJson(day, next, "FULL")))   // 2 日 > 残 1 日
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-004"));
    }

    @Test
    void endDateBeforeStartDateIsRejected() throws Exception {
        mvc.perform(authPost("/api/requests", employeeToken, leaveJson(day.plusDays(3), day, "FULL")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-003"));
    }

    @Test
    void halfDayLeaveConsumesHalfADay() throws Exception {
        long requestId = createLeave(employeeToken, day, day, "AM");
        mvc.perform(authPost("/api/approvals/" + stepIdFor(managerToken, requestId) + "/approve", managerToken, "{}")).andExpect(status().isOk());
        mvc.perform(authPost("/api/approvals/" + stepIdFor(adminToken, requestId) + "/approve", adminToken, "{}")).andExpect(status().isOk());
        assertEquals(19.5, remainingDays(employeeToken), 0.001);
    }

    @Test
    void approvingTheSameStepTwiceIsRejected() throws Exception {
        long requestId = createLeave(employeeToken, day, day, "FULL");
        long stepId = stepIdFor(managerToken, requestId);
        mvc.perform(authPost("/api/approvals/" + stepId + "/approve", managerToken, "{}")).andExpect(status().isOk());
        // 処理済みのステップは、承認・差戻し・却下のどれでももう動かせない（二重処理の防止）
        mvc.perform(authPost("/api/approvals/" + stepId + "/approve", managerToken, "{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-011"));
        mvc.perform(authPost("/api/approvals/" + stepId + "/reject", managerToken, "{\"comment\":\"やっぱり却下\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-011"));
    }

    @Test
    void anApproverCannotApproveTheirOwnRequest() throws Exception {
        // 上長のいない管理者が申請すると、承認は管理者の最終承認 1 段だけになる。自分の申請は自分で承認できない
        User selfAdmin = createUser("ADMIN", null);
        grantAnnualLeave(selfAdmin, "20.0");
        String selfToken = login(selfAdmin);
        long requestId = createLeave(selfToken, day, day, "FULL");
        long stepId = stepIdFor(adminToken, requestId);   // 別の管理者の承認待ちには載る

        mvc.perform(authPost("/api/approvals/" + stepId + "/approve", selfToken, "{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("E-009"));
        // 別の管理者なら承認できる
        mvc.perform(authPost("/api/approvals/" + stepId + "/approve", adminToken, "{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestStatus").value("APPROVED"));
    }

    // ---------------------------------------------------------------- ヘルパー

    private LocalDate nextBusinessDay(LocalDate d) {
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY || holidayService.isHoliday(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    private String leaveJson(LocalDate start, LocalDate end, String unit) {
        return "{\"requestType\":\"LEAVE\",\"leaveTypeId\":" + annualLeaveTypeId() + ",\"startDate\":\"" + start
                + "\",\"endDate\":\"" + end + "\",\"unit\":\"" + unit + "\",\"reason\":\"結合テスト\",\"asDraft\":false}";
    }

    private long createLeave(String token, LocalDate start, LocalDate end, String unit) throws Exception {
        MvcResult res = mvc.perform(authPost("/api/requests", token, leaveJson(start, end, unit)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        return ((Number) JsonPath.read(res.getResponse().getContentAsString(), "$.requestId")).longValue();
    }

    private JSONArray pendingFor(String token, long requestId, String field) throws Exception {
        MvcResult res = mvc.perform(authGet("/api/approvals/pending", token)).andExpect(status().isOk()).andReturn();
        return json(res, "$.items[?(@.requestId==" + requestId + ")]." + field);
    }

    private int pendingCount(String token, long requestId) throws Exception {
        return pendingFor(token, requestId, "stepId").size();
    }

    private long stepIdFor(String token, long requestId) throws Exception {
        JSONArray ids = pendingFor(token, requestId, "stepId");
        assertEquals(1, ids.size(), "承認待ちにこの申請が 1 件あるはず");
        return ((Number) ids.get(0)).longValue();
    }

    private int pendingStepNo(String token, long requestId) throws Exception {
        JSONArray nos = pendingFor(token, requestId, "stepNo");
        assertEquals(1, nos.size(), "承認待ちにこの申請が 1 件あるはず");
        return ((Number) nos.get(0)).intValue();
    }

    private double remainingDays(String token) throws Exception {
        MvcResult res = mvc.perform(authGet("/api/leave-balances", token)).andExpect(status().isOk()).andReturn();
        JSONArray r = json(res, "$.balances[?(@.leaveTypeName=='年次有給休暇')].remainingDays");
        return ((Number) r.get(0)).doubleValue();
    }
}
