package com.example.kintaiflow.service;

import com.example.kintaiflow.config.AppTime;
import com.example.kintaiflow.dto.ApprovalResultResponse;
import com.example.kintaiflow.dto.PendingApprovalItem;
import com.example.kintaiflow.dto.PendingApprovalListResponse;
import com.example.kintaiflow.dto.PendingApprovalRow;
import com.example.kintaiflow.entity.ApprovalStep;
import com.example.kintaiflow.entity.AttendanceRecord;
import com.example.kintaiflow.entity.NotificationType;
import com.example.kintaiflow.entity.Request;
import com.example.kintaiflow.entity.RequestStatus;
import com.example.kintaiflow.entity.RequestType;
import com.example.kintaiflow.entity.StepStatus;
import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.exception.BusinessException;
import com.example.kintaiflow.repository.ApprovalStepRepository;
import com.example.kintaiflow.repository.AttendanceRecordRepository;
import com.example.kintaiflow.repository.RequestRepository;
import com.example.kintaiflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * ONL-009 承認待ち一覧 / ONL-010 承認 / ONL-011 差戻し / ONL-012 却下。
 * 権限確認・状態確認・排他制御・通知は 3 操作で共通（ONL-010 が定義元）。
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final ApprovalStepRepository approvalStepRepository;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceCalculator attendanceCalculator;
    private final LeaveBalanceService leaveBalanceService;
    private final ApprovalRouteService approvalRouteService;
    private final NotificationService notificationService;
    private final Clock clock;

    public ApprovalService(ApprovalStepRepository approvalStepRepository,
                           RequestRepository requestRepository,
                           UserRepository userRepository,
                           AttendanceRecordRepository attendanceRecordRepository,
                           AttendanceCalculator attendanceCalculator,
                           LeaveBalanceService leaveBalanceService,
                           ApprovalRouteService approvalRouteService,
                           NotificationService notificationService,
                           Clock clock) {
        this.approvalStepRepository = approvalStepRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceCalculator = attendanceCalculator;
        this.leaveBalanceService = leaveBalanceService;
        this.approvalRouteService = approvalRouteService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /** 処理対象のステップと、行ロック済みの申請。 */
    private record ApprovalContext(ApprovalStep step, Request request) {}

    // ───────────────────────── ONL-009 承認待ち一覧 ─────────────────────────

    /** 実行者が今処理できる承認ステップを提出日の古い順に返す。 */
    @Transactional(readOnly = true)
    public PendingApprovalListResponse listPending(Long userId, String requestType, Integer stepNo) {
        User actor = requireApprover(userId);
        if (requestType != null && !List.of(RequestType.LEAVE, RequestType.CLOCK_CORRECTION).contains(requestType)) {
            throw invalidInput();
        }
        if (stepNo != null && stepNo != 1 && stepNo != 2) {
            throw invalidInput();
        }
        boolean admin = ROLE_ADMIN.equals(actor.getRole());
        List<Integer> allowed = admin ? List.of(1, 2) : List.of(1);   // MANAGER は 1段目のみ
        List<Integer> stepNos = (stepNo == null) ? allowed
                : (allowed.contains(stepNo) ? List.of(stepNo) : List.<Integer>of());
        if (stepNos.isEmpty()) {
            return new PendingApprovalListResponse(List.of());
        }
        List<String> types = (requestType == null)
                ? List.of(RequestType.LEAVE, RequestType.CLOCK_CORRECTION) : List.of(requestType);
        List<PendingApprovalRow> rows = approvalStepRepository.findPending(userId, admin, types, stepNos);
        return new PendingApprovalListResponse(rows.stream().map(ApprovalService::toItem).toList());
    }

    // ───────────────────────── ONL-010 承認 ─────────────────────────

    /** 承認。1段目は申請を2段目へ進め、2段目は申請を確定して反映処理と通知を行う。 */
    @Transactional
    public ApprovalResultResponse approve(Long stepId, Long userId, String comment) {
        String note = normalizeComment(comment);
        User actor = requireApprover(userId);
        ApprovalContext ctx = loadContext(stepId, actor);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        recordStep(ctx, actor, StepStatus.APPROVED, note, now);
        Request req = ctx.request();
        String result;
        if (ctx.step().getStepNo() == ApprovalRouteService.STEP_MANAGER) {   // 1段目（上長）
            advanceToFinalStep(ctx);
            notifyApplicant(req, NotificationType.REQUEST_APPROVED,
                    describe(req) + "は上長が承認しました。管理者の最終承認待ちです。");
            notifyApprovers(req);
            result = RequestStatus.PENDING;
        } else {                                                             // 2段目（管理者＝最終承認）
            updateRequestStatus(ctx, RequestStatus.APPROVED);
            applyApproved(req);
            notifyApplicant(req, NotificationType.REQUEST_APPROVED, describe(req) + "が承認されました。");
            result = RequestStatus.APPROVED;
        }
        log.info("approval processed: action=APPROVE, stepId={}, requestId={}, stepNo={}, actorId={}, requestStatus={}",
                stepId, req.getId(), ctx.step().getStepNo(), userId, result);
        return new ApprovalResultResponse(result);
    }

    // ───────────────────────── ONL-011 差戻し ─────────────────────────

    /** 差戻し。コメント必須。ステップと申請を RETURNED にして申請者へ通知する。 */
    @Transactional
    public ApprovalResultResponse returnBack(Long stepId, Long userId, String comment) {
        String note = requireComment(comment);   // 入力チェックを権限判定より先に行う
        User actor = requireApprover(userId);
        ApprovalContext ctx = loadContext(stepId, actor);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        recordStep(ctx, actor, StepStatus.RETURNED, note, now);
        updateRequestStatus(ctx, RequestStatus.RETURNED);
        Request req = ctx.request();
        notifyApplicant(req, NotificationType.REQUEST_RETURNED,
                describe(req) + "が差戻しされました。申請詳細のコメントを確認してください。");
        log.info("approval processed: action=RETURN, stepId={}, requestId={}, stepNo={}, actorId={}, requestStatus=RETURNED",
                stepId, req.getId(), ctx.step().getStepNo(), userId);
        return new ApprovalResultResponse(RequestStatus.RETURNED);
    }

    // ───────────────────────── ONL-012 却下 ─────────────────────────

    /** 却下。コメント必須。ステップと申請を REJECTED（終了状態）にして申請者へ通知する。 */
    @Transactional
    public ApprovalResultResponse reject(Long stepId, Long userId, String comment) {
        String note = requireComment(comment);
        User actor = requireApprover(userId);
        ApprovalContext ctx = loadContext(stepId, actor);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        recordStep(ctx, actor, StepStatus.REJECTED, note, now);
        updateRequestStatus(ctx, RequestStatus.REJECTED);
        Request req = ctx.request();
        notifyApplicant(req, NotificationType.REQUEST_REJECTED,
                describe(req) + "が却下されました。申請詳細のコメントを確認してください。");
        log.info("approval processed: action=REJECT, stepId={}, requestId={}, stepNo={}, actorId={}, requestStatus=REJECTED",
                stepId, req.getId(), ctx.step().getStepNo(), userId);
        return new ApprovalResultResponse(RequestStatus.REJECTED);
    }

    // ───────────────────────── 共通処理 ─────────────────────────

    /** 実行者を DB から取得し、ACTIVE かつ MANAGER/ADMIN であることを確認する（JWT の role を過信しない）。 */
    private User requireApprover(Long userId) {
        User u = userRepository.findById(userId).orElse(null);
        if (u == null || !STATUS_ACTIVE.equals(u.getStatus())
                || (!ROLE_MANAGER.equals(u.getRole()) && !ROLE_ADMIN.equals(u.getRole()))) {
            log.warn("approver check failed: userId={}", userId);
            throw forbidden();
        }
        return u;
    }

    /** ステップ所有確認 → 申請の行ロック取得 → 自己承認禁止 → 状態確認。判定順は E-010 → E-009 → E-011 固定。 */
    private ApprovalContext loadContext(Long stepId, User actor) {
        ApprovalStep step = approvalStepRepository.findById(stepId).orElseThrow(ApprovalService::forbidden);
        boolean mine = (step.getStepNo() == ApprovalRouteService.STEP_MANAGER)
                ? actor.getId().equals(step.getApproverId())    // 1段目：指名された本人
                : ROLE_ADMIN.equals(actor.getRole());           // 2段目：管理者なら誰でも
        if (!mine) {
            throw forbidden();
        }
        Request req = requestRepository.findByIdForUpdate(step.getRequestId()).orElseThrow(ApprovalService::forbidden);
        if (req.getUserId().equals(actor.getId())) {
            throw new BusinessException("E-009", "自分の申請は承認できません。", HttpStatus.FORBIDDEN);
        }
        if (!StepStatus.WAITING.equals(step.getStatus())
                || !RequestStatus.PENDING.equals(req.getStatus())
                || !req.getCurrentStep().equals(step.getStepNo())) {
            throw alreadyProcessed();
        }
        return new ApprovalContext(step, req);
    }

    /** ステップを処理済みに更新（WAITING 条件付き）。0 件は競合として E-011。 */
    private void recordStep(ApprovalContext ctx, User actor, String newStatus, String note, LocalDateTime now) {
        int n = approvalStepRepository.actIfWaiting(ctx.step().getId(), newStatus, actor.getId(), note, now);
        if (n != 1) {
            throw alreadyProcessed();
        }
    }

    /** 1段目承認後、申請を2段目へ進める（status は PENDING のまま）。 */
    private void advanceToFinalStep(ApprovalContext ctx) {
        int n = requestRepository.advanceStepIfPending(ctx.request().getId(), 1, 2);
        if (n != 1) {
            throw alreadyProcessed();
        }
    }

    /** 申請の状態を最終状態（APPROVED / RETURNED / REJECTED）へ更新する（current_step は変えない）。 */
    private void updateRequestStatus(ApprovalContext ctx, String newStatus) {
        int n = requestRepository.updateStatusIfPending(ctx.request().getId(), ctx.step().getStepNo(), newStatus);
        if (n != 1) {
            throw alreadyProcessed();
        }
    }

    /** 最終承認の副作用。休暇は残日数の消化、打刻修正は attendance_records への反映。 */
    private void applyApproved(Request req) {
        if (RequestType.LEAVE.equals(req.getRequestType())) {
            // 利用日＝開始日。UNLIMITED 種別は consume 内で何もしない
            leaveBalanceService.consume(req.getUserId(), req.getLeaveTypeId(), req.getDays(), req.getStartDate());
        } else if (RequestType.CLOCK_CORRECTION.equals(req.getRequestType())) {
            applyClockCorrection(req);
        }
    }

    /** 打刻修正を attendance_records へ反映する。対象日の行が無ければ新規作成し、勤務・残業時間を再計算する。 */
    private void applyClockCorrection(Request req) {
        if (req.getCorrectedClockIn() == null || req.getCorrectedClockOut() == null) {
            throw new IllegalStateException("corrected clock is missing: requestId=" + req.getId());
        }
        LocalDate workDate = req.getStartDate();
        AttendanceRecord rec = attendanceRecordRepository
                .findWithLockByUserIdAndWorkDate(req.getUserId(), workDate)
                .orElseGet(() -> new AttendanceRecord(req.getUserId(), workDate));
        rec.setClockIn(req.getCorrectedClockIn());
        rec.setClockOut(req.getCorrectedClockOut());
        AttendanceCalculator.WorkTime wt = attendanceCalculator.calculate(rec.getClockIn(), rec.getClockOut());
        rec.setWorkMinutes(wt.workMinutes());
        rec.setOvertimeMinutes(wt.overtimeMinutes());
        attendanceRecordRepository.save(rec);
    }

    private void notifyApplicant(Request req, String type, String text) {
        notificationService.notify(req.getUserId(), type, text, req.getId());
    }

    /** 1段目承認後、2段目の承認者（有効な全管理者・申請者除く）へ最終承認を依頼する。0 件でもエラーにしない。 */
    private void notifyApprovers(Request req) {
        ApprovalStep next = approvalStepRepository.findByRequestIdOrderByStepNo(req.getId()).stream()
                .filter(s -> s.getStepNo() == ApprovalRouteService.STEP_ADMIN).findFirst()
                .orElseThrow(() -> new IllegalStateException("step 2 not found: requestId=" + req.getId()));
        User applicant = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new IllegalStateException("applicant not found: requestId=" + req.getId()));
        String text = applicant.getName() + "さんの" + describe(req) + "の最終承認をお願いします。";
        List<Long> ids = approvalRouteService.resolveCurrentApproverIds(next, req.getUserId());
        if (ids.isEmpty()) {
            log.warn("no active admin to notify: requestId={}", req.getId());
        }
        for (Long id : ids) {
            notificationService.notify(id, NotificationType.REQUEST_SUBMITTED, text, req.getId());
        }
    }

    /** 通知文に使う申請の表記。 */
    private static String describe(Request req) {
        if (RequestType.LEAVE.equals(req.getRequestType())) {
            String period = req.getStartDate().equals(req.getEndDate()) ? req.getStartDate().toString()
                    : req.getStartDate() + "〜" + req.getEndDate();
            return "休暇申請（" + period + "）";
        }
        return "打刻修正申請（対象日 " + req.getStartDate() + "）";
    }

    private static PendingApprovalItem toItem(PendingApprovalRow r) {
        return new PendingApprovalItem(r.stepId(), r.stepNo(), r.requestId(), r.requestType(),
                r.applicantId(), r.applicantName(), r.departmentName(),
                r.startDate(), r.endDate(), r.days(),
                AppTime.toIso(r.submittedAt()), AppTime.toIso(r.step1ApprovedAt()));
    }

    /** 空・空白のみ（全角含む）は null、それ以外は前後の空白を除去。 */
    static String normalizeComment(String comment) {
        if (comment == null) return null;
        String t = comment.strip();
        return t.isEmpty() ? null : t;
    }

    /** 差戻し・却下用のコメント必須確認（E-008）。 */
    private static String requireComment(String comment) {
        String c = normalizeComment(comment);
        if (c == null) {
            throw new BusinessException("E-008", "差戻し・却下時はコメントを入力してください。", HttpStatus.BAD_REQUEST);
        }
        return c;
    }

    private static BusinessException invalidInput() {
        return new BusinessException("E-002", "入力内容が正しくありません。", HttpStatus.BAD_REQUEST);
    }

    private static BusinessException forbidden() {
        return new BusinessException("E-010", "この操作を行う権限がありません。", HttpStatus.FORBIDDEN);
    }

    private static BusinessException alreadyProcessed() {
        return new BusinessException("E-011", "この申請は既に処理されています。", HttpStatus.CONFLICT);
    }
}
