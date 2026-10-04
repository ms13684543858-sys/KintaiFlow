package com.example.kintaiflow.service;

import com.example.kintaiflow.config.AppTime;
import com.example.kintaiflow.dto.*;
import com.example.kintaiflow.entity.*;
import com.example.kintaiflow.exception.BusinessException;
import com.example.kintaiflow.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** ONL-005 申請作成 / ONL-006 申請一覧取得 / ONL-007 申請詳細取得 / ONL-008 申請取下げ / ONL-022 申請提出。 */
@Service
public class RequestService {

    private static final Logger log = LoggerFactory.getLogger(RequestService.class);

    /** 休暇申請の最大期間（日）。 */
    static final int MAX_PERIOD_DAYS = 1100;
    /** 申請状態の全値（ONL-006 の絞込検証用）。 */
    private static final Set<String> ALL_STATUSES = Set.of(RequestStatus.DRAFT, RequestStatus.PENDING,
            RequestStatus.RETURNED, RequestStatus.REJECTED, RequestStatus.APPROVED, RequestStatus.WITHDRAWN);
    /** 取下げ可能な状態（承認前）。 */
    private static final Set<String> WITHDRAWABLE = Set.of(RequestStatus.DRAFT, RequestStatus.PENDING,
            RequestStatus.RETURNED);
    /** 提出（再提出）可能な状態。 */
    private static final Set<String> SUBMITTABLE = Set.of(RequestStatus.DRAFT, RequestStatus.RETURNED);
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final RequestRepository requestRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final UserRepository userRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final DepartmentRepository departmentRepository;
    private final HolidayService holidayService;
    private final LeaveBalanceService leaveBalanceService;
    private final NotificationService notificationService;
    private final ApprovalRouteService approvalRouteService;
    private final Clock clock;

    public RequestService(RequestRepository requestRepository,
                          ApprovalStepRepository approvalStepRepository,
                          UserRepository userRepository,
                          LeaveTypeRepository leaveTypeRepository,
                          DepartmentRepository departmentRepository,
                          HolidayService holidayService,
                          LeaveBalanceService leaveBalanceService,
                          NotificationService notificationService,
                          ApprovalRouteService approvalRouteService,
                          Clock clock) {
        this.requestRepository = requestRepository;
        this.approvalStepRepository = approvalStepRepository;
        this.userRepository = userRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.departmentRepository = departmentRepository;
        this.holidayService = holidayService;
        this.leaveBalanceService = leaveBalanceService;
        this.notificationService = notificationService;
        this.approvalRouteService = approvalRouteService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ ONL-005

    @Transactional
    public CreateRequestResponse create(Long userId, CreateRequestRequest req) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        boolean draft = Boolean.TRUE.equals(req.asDraft());
        boolean leave = RequestType.LEAVE.equals(req.requestType());

        validateInput(req, today, draft);                                    // ② E-002 / E-003
        User user = userRepository.findByIdForUpdate(userId)                 // ③ 行ロック
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .orElseThrow(RequestService::forbidden);
        LocalDate endDate = leave ? req.endDate() : req.startDate();
        LeaveType leaveType = null;
        BigDecimal days = null;
        if (leave) {
            leaveType = leaveTypeRepository.findById(req.leaveTypeId())      // ④
                    .filter(t -> Boolean.TRUE.equals(t.getIsActive()))
                    .orElseThrow(() -> badInput("休暇種別が正しくありません。"));
            if (!LeaveUnit.FULL.equals(req.unit()) && !Boolean.TRUE.equals(leaveType.getAllowHalfDay())) {
                throw badInput("この休暇種別では半日単位を指定できません。");
            }
            days = calcDays(req.startDate(), endDate, req.unit());           // ⑤
            if (days.signum() == 0) {
                throw badInput("指定期間に取得できる営業日がありません。");
            }
        }
        if (!draft) {                                                        // ⑥⑦ 提出時のみ
            checkSubmittable(userId, req.requestType(), leaveType, days, req.startDate(), endDate, req.unit(), today);
        }
        Request r = new Request();                                           // ⑧
        r.setUserId(userId);
        r.setRequestType(req.requestType());
        r.setStartDate(req.startDate());
        r.setEndDate(endDate);
        r.setLeaveTypeId(leave ? req.leaveTypeId() : null);
        r.setUnit(leave ? req.unit() : null);
        r.setDays(days);
        r.setCorrectedClockIn(leave ? null : req.startDate().atTime(LocalTime.parse(req.correctedClockIn())));
        r.setCorrectedClockOut(leave ? null : req.startDate().atTime(LocalTime.parse(req.correctedClockOut())));
        r.setReason(blankToNull(req.reason()));
        r.setStatus(draft ? RequestStatus.DRAFT : RequestStatus.PENDING);
        r.setCurrentStep(draft ? 0 : 1);
        r.setSubmittedAt(draft ? null : now);
        r = requestRepository.save(r);
        if (!draft) {
            startApproval(r, user, leaveType);                               // ⑨⑩
        }
        log.info("[ONL-005] request created id={} userId={} type={} status={}",
                r.getId(), userId, r.getRequestType(), r.getStatus());
        return new CreateRequestResponse(r.getId(), r.getStatus());
    }

    /** 提出時にだけ行う検証（ONL-005 / ONL-022 共用）。呼出し側で申請者の users 行ロック取得済みであること。 */
    void checkSubmittable(Long userId, String requestType, LeaveType leaveType, BigDecimal days,
                          LocalDate start, LocalDate end, String unit, LocalDate today) {
        if (RequestType.LEAVE.equals(requestType)) {
            checkLeaveBalance(userId, leaveType, days, today);               // ⑥ E-004
            checkOverlap(userId, start, end, unit);                          // ⑦ E-005
        } else if (requestRepository.existsByUserIdAndRequestTypeAndStartDateAndStatus(
                userId, RequestType.CLOCK_CORRECTION, start, RequestStatus.PENDING)) {
            throw new BusinessException("E-005", "同一期間に既に申請があります。", HttpStatus.CONFLICT);
        }
    }

    private void validateInput(CreateRequestRequest req, LocalDate today, boolean draft) {
        boolean leave = RequestType.LEAVE.equals(req.requestType());
        if (req.startDate() == null) {
            throw badInput(leave ? "開始日は必須入力です。" : "対象日は必須入力です。");
        }
        if (leave) {
            if (req.leaveTypeId() == null) throw badInput("休暇種別は必須入力です。");
            if (req.endDate() == null) throw badInput("終了日は必須入力です。");
            if (req.unit() == null) throw badInput("取得単位は必須入力です。");
            if (req.endDate().isBefore(req.startDate())) {
                throw new BusinessException("E-003", "開始日は終了日以前の日付を指定してください。", HttpStatus.BAD_REQUEST);
            }
            if (ChronoUnit.DAYS.between(req.startDate(), req.endDate()) + 1 > MAX_PERIOD_DAYS) {
                throw badInput("期間は1,100日以内で指定してください。");
            }
            if (!LeaveUnit.FULL.equals(req.unit()) && !req.startDate().equals(req.endDate())) {
                throw badInput("半休は開始日と終了日に同じ日付を指定してください。");
            }
        } else {
            if (req.correctedClockIn() == null) throw badInput("修正後の出勤時刻は必須入力です。");
            if (req.correctedClockOut() == null) throw badInput("修正後の退勤時刻は必須入力です。");
            if (req.startDate().isAfter(today)) {
                throw new BusinessException("E-003", "対象日は本日以前の日付を指定してください。", HttpStatus.BAD_REQUEST);
            }
            if (!LocalTime.parse(req.correctedClockOut()).isAfter(LocalTime.parse(req.correctedClockIn()))) {
                throw new BusinessException("E-003", "修正後の退勤時刻は出勤時刻より後の時刻を指定してください。",
                        HttpStatus.BAD_REQUEST);
            }
            if (!draft && (req.reason() == null || req.reason().isBlank())) {
                throw badInput("理由は必須入力です。");
            }
        }
    }

    /** 期間内の営業日数（土日・祝日・会社休日を除く）。AM / PM は 0.5（営業日でなければ 0.0）。 */
    public BigDecimal calcDays(LocalDate start, LocalDate end, String unit) {
        Set<LocalDate> holidays = holidayService.getHolidayDates(start, end);
        int business = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DayOfWeek w = d.getDayOfWeek();
            if (w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY || holidays.contains(d)) continue;
            business++;
        }
        if (!LeaveUnit.FULL.equals(unit)) {
            return business == 0 ? new BigDecimal("0.0") : new BigDecimal("0.5");
        }
        return BigDecimal.valueOf(business).setScale(1);
    }

    private void checkLeaveBalance(Long userId, LeaveType type, BigDecimal days, LocalDate today) {
        if (!"LIMITED".equals(type.getMaxDaysRule())) return;
        BigDecimal remaining = leaveBalanceService.getRemainingDays(userId, type.getId(), today);
        BigDecimal pending = requestRepository.sumPendingLeaveDays(userId, type.getId());
        if (pending == null) pending = BigDecimal.ZERO;
        BigDecimal available = remaining.subtract(pending);
        if (days.compareTo(available) > 0) {
            throw new BusinessException("E-004", "休暇残日数が不足しています。", HttpStatus.CONFLICT);
        }
    }

    private void checkOverlap(Long userId, LocalDate start, LocalDate end, String unit) {
        List<Request> list = requestRepository.findOverlappingLeaves(userId, start, end, RequestStatus.BLOCKING);
        for (Request ex : list) {
            // 同じ1日に対する午前半休と午後半休の組み合わせだけは重複としない
            boolean amPm = !LeaveUnit.FULL.equals(unit) && !LeaveUnit.FULL.equals(ex.getUnit())
                    && !unit.equals(ex.getUnit())
                    && ex.getStartDate().equals(start) && ex.getEndDate().equals(start);
            if (!amPm) {
                throw new BusinessException("E-005", "同一期間に既に申請があります。", HttpStatus.CONFLICT);
            }
        }
    }

    /** 承認ルート作成と現在の承認者への通知（ONL-005 / ONL-022 共用）。r は INSERT 済みであること。 */
    void startApproval(Request r, User applicant, LeaveType leaveType) {
        List<ApprovalStep> steps = approvalRouteService.createSteps(r, applicant);
        r.setCurrentStep(steps.get(0).getStepNo());
        String msg = buildSubmittedMessage(applicant, r, leaveType);
        for (Long approverId : approvalRouteService.resolveCurrentApproverIds(steps.get(0), applicant.getId())) {
            notificationService.notify(approverId, NotificationType.REQUEST_SUBMITTED, msg, r.getId());
        }
    }

    private String buildSubmittedMessage(User applicant, Request r, LeaveType type) {
        if (RequestType.LEAVE.equals(r.getRequestType())) {
            return applicant.getName() + "さんから休暇申請（" + (type == null ? "" : type.getName()) + " "
                    + r.getStartDate() + "〜" + r.getEndDate() + "）が提出されました。";
        }
        return applicant.getName() + "さんから打刻修正申請（対象日 " + r.getStartDate() + "）が提出されました。";
    }

    // ------------------------------------------------------------------ ONL-006

    @Transactional(readOnly = true)
    public RequestListResponse list(Long userId, String status) {
        boolean filtered = status != null && !status.isBlank();
        if (filtered && !ALL_STATUSES.contains(status)) {
            throw new BusinessException("E-002", "状態の指定が正しくありません。", HttpStatus.BAD_REQUEST);
        }
        List<Request> rows = filtered
                ? requestRepository.findAllByUserAndStatus(userId, status)
                : requestRepository.findAllByUser(userId);
        Set<Long> typeIds = rows.stream().map(Request::getLeaveTypeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = typeIds.isEmpty() ? Map.of()
                : leaveTypeRepository.findAllById(typeIds).stream()
                .collect(Collectors.toMap(LeaveType::getId, LeaveType::getName));
        List<RequestSummary> items = rows.stream()
                .map(r -> toSummary(r, r.getLeaveTypeId() == null ? null : names.get(r.getLeaveTypeId())))
                .toList();
        return new RequestListResponse(items);
    }

    private RequestSummary toSummary(Request r, String leaveTypeName) {
        return new RequestSummary(r.getId(), r.getRequestType(), leaveTypeName, r.getStartDate(), r.getEndDate(),
                r.getUnit(), r.getDays(), r.getStatus(),
                AppTime.toIso(r.getSubmittedAt()), AppTime.toIso(r.getCreatedAt()));
    }

    // ------------------------------------------------------------------ ONL-007

    @Transactional(readOnly = true)
    public RequestDetailResponse getDetail(Long requestId, Long viewerId) {
        Request r = requestRepository.findById(requestId).orElseThrow(RequestService::forbidden);
        List<ApprovalStep> steps = approvalStepRepository.findByRequestIdOrderByStepNo(requestId);
        assertCanView(r, steps, viewerId);
        Set<Long> userIds = new HashSet<>();
        userIds.add(r.getUserId());
        steps.stream().map(ApprovalStep::getApproverId).filter(Objects::nonNull).forEach(userIds::add);
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        User applicant = users.get(r.getUserId());
        String dept = applicant != null && applicant.getDepartmentId() != null
                ? departmentRepository.findById(applicant.getDepartmentId()).map(Department::getName).orElse(null)
                : null;
        LeaveType type = r.getLeaveTypeId() == null ? null : leaveTypeRepository.findById(r.getLeaveTypeId()).orElse(null);
        BigDecimal balance = null;
        if (RequestType.LEAVE.equals(r.getRequestType()) && type != null && "LIMITED".equals(type.getMaxDaysRule())) {
            balance = leaveBalanceService.getRemainingDays(r.getUserId(), type.getId(), LocalDate.now(clock));
        }
        return new RequestDetailResponse(toDetail(r, applicant, dept, type), toSteps(r, steps, users), balance);
    }

    private void assertCanView(Request r, List<ApprovalStep> steps, Long viewerId) {
        if (r.getUserId().equals(viewerId)) return;                               // 本人
        if (RequestStatus.DRAFT.equals(r.getStatus())) throw forbidden();         // 下書きは本人のみ
        if (steps.stream().anyMatch(s -> viewerId.equals(s.getApproverId()))) return;
        User viewer = userRepository.findById(viewerId).orElseThrow(RequestService::forbidden);
        if ("ADMIN".equals(viewer.getRole())) return;
        throw forbidden();
    }

    private RequestDetail toDetail(Request r, User applicant, String dept, LeaveType type) {
        return new RequestDetail(r.getId(), r.getRequestType(), r.getUserId(),
                applicant == null ? null : applicant.getName(), dept,
                r.getLeaveTypeId(), type == null ? null : type.getName(),
                r.getStartDate(), r.getEndDate(), r.getUnit(), r.getDays(),
                formatTime(r.getCorrectedClockIn()), formatTime(r.getCorrectedClockOut()),
                r.getReason(), r.getStatus(), r.getCurrentStep(),
                AppTime.toIso(r.getSubmittedAt()), AppTime.toIso(r.getCreatedAt()));
    }

    private List<StepDetail> toSteps(Request r, List<ApprovalStep> steps, Map<Long, User> users) {
        return steps.stream().map(s -> {
            User approver = s.getApproverId() == null ? null : users.get(s.getApproverId());
            boolean current = RequestStatus.PENDING.equals(r.getStatus())
                    && s.getStepNo().equals(r.getCurrentStep())
                    && StepStatus.WAITING.equals(s.getStatus());
            return new StepDetail(s.getStepNo(), s.getStepNo() == 1 ? "上長" : "管理者",
                    s.getApproverId(), approver == null ? null : approver.getName(),
                    s.getStatus(), s.getComment(), AppTime.toIso(s.getActedAt()), current);
        }).toList();
    }

    private static String formatTime(LocalDateTime t) {
        return t == null ? null : t.toLocalTime().format(HHMM);
    }

    // ------------------------------------------------------------------ ONL-008

    @Transactional
    public WithdrawResponse withdraw(Long requestId, Long userId) {
        Request r = requestRepository.findByIdForUpdate(requestId).orElseThrow(RequestService::forbidden);
        if (!r.getUserId().equals(userId)) {
            throw forbidden();
        }
        String fromStatus = r.getStatus();
        if (!WITHDRAWABLE.contains(fromStatus)) {
            throw new BusinessException("E-011", "この申請は既に処理されています。", HttpStatus.CONFLICT);
        }
        r.setStatus(RequestStatus.WITHDRAWN);
        requestRepository.save(r);
        if (RequestStatus.PENDING.equals(fromStatus)) {
            User applicant = userRepository.findById(userId).orElseThrow(RequestService::forbidden);
            ApprovalStep current = approvalStepRepository.findByRequestIdOrderByStepNo(requestId).stream()
                    .filter(s -> s.getStepNo().equals(r.getCurrentStep()))
                    .findFirst().orElse(null);
            if (current != null) {
                String msg = buildWithdrawnMessage(applicant, r);
                for (Long approverId : approvalRouteService.resolveCurrentApproverIds(current, userId)) {
                    notificationService.notify(approverId, NotificationType.REQUEST_WITHDRAWN, msg, r.getId());
                }
            }
        }
        log.info("[ONL-008] request withdrawn id={} userId={} fromStatus={}", requestId, userId, fromStatus);
        return new WithdrawResponse(r.getStatus());
    }

    private String buildWithdrawnMessage(User applicant, Request r) {
        String kind = RequestType.LEAVE.equals(r.getRequestType()) ? "休暇申請" : "打刻修正申請";
        String period = r.getStartDate().equals(r.getEndDate())
                ? r.getStartDate().toString() : r.getStartDate() + "〜" + r.getEndDate();
        return applicant.getName() + "さんが" + kind + "（" + period + "）を取り下げました。";
    }

    // ------------------------------------------------------------------ ONL-022

    @Transactional
    public SubmitRequestResponse submit(Long requestId, Long userId) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        User user = userRepository.findByIdForUpdate(userId)                  // ② users → requests の順でロック
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .orElseThrow(RequestService::forbidden);
        Request r = requestRepository.findByIdForUpdate(requestId)            // ③
                .orElseThrow(RequestService::notFound);
        if (!r.getUserId().equals(userId)) {                                  // ④
            throw forbidden();
        }
        String fromStatus = r.getStatus();
        if (!SUBMITTABLE.contains(fromStatus)) {                              // ⑤
            throw notSubmittable();
        }
        LeaveType leaveType = revalidateForSubmit(r);                         // ⑥
        checkSubmittable(userId, r.getRequestType(), leaveType, r.getDays(),
                r.getStartDate(), r.getEndDate(), r.getUnit(), today);        // ⑦
        r.setStatus(RequestStatus.PENDING);                                   // ⑧
        r.setCurrentStep(1);
        r.setSubmittedAt(now);
        approvalStepRepository.deleteByRequestId(requestId);                  // ⑨ バルク削除（先に変更をフラッシュ）
        startApproval(r, user, leaveType);                                    // ⑩
        log.info("[ONL-022] request submitted id={} userId={} fromStatus={}", requestId, userId, fromStatus);
        return new SubmitRequestResponse(r.getId(), r.getStatus());
    }

    /** 下書き・差戻しの内容を提出条件で再検証する。LEAVE は days を再計算して r に設定し種別を返す。 */
    private LeaveType revalidateForSubmit(Request r) {
        if (RequestType.LEAVE.equals(r.getRequestType())) {
            LeaveType type = (r.getLeaveTypeId() == null ? java.util.Optional.<LeaveType>empty()
                    : leaveTypeRepository.findById(r.getLeaveTypeId()))
                    .filter(t -> Boolean.TRUE.equals(t.getIsActive()))
                    .orElseThrow(() -> badInput("休暇種別が正しくありません。"));
            if (!LeaveUnit.FULL.equals(r.getUnit()) && !Boolean.TRUE.equals(type.getAllowHalfDay())) {
                throw badInput("この休暇種別では半日単位を指定できません。");
            }
            BigDecimal days = calcDays(r.getStartDate(), r.getEndDate(), r.getUnit());
            if (days.signum() == 0) {
                throw badInput("指定期間に取得できる営業日がありません。");
            }
            r.setDays(days);
            return type;
        }
        if (r.getReason() == null || r.getReason().isBlank()) {
            throw badInput("理由は必須入力です。");
        }
        if (r.getCorrectedClockIn() == null || r.getCorrectedClockOut() == null) {
            throw badInput("修正後の出勤・退勤時刻は必須入力です。");
        }
        if (!r.getCorrectedClockOut().isAfter(r.getCorrectedClockIn())) {
            throw new BusinessException("E-003", "修正後の退勤時刻は出勤時刻より後の時刻を指定してください。",
                    HttpStatus.BAD_REQUEST);
        }
        return null;
    }

    // ------------------------------------------------------------------ 共通

    /** 権限なし／対象なし（ONL-005〜008 共用）。E-010 / 403。 */
    private static BusinessException forbidden() {
        return new BusinessException("E-010", "この操作を行う権限がありません。", HttpStatus.FORBIDDEN);
    }

    private static BusinessException badInput(String message) {
        return new BusinessException("E-002", message, HttpStatus.BAD_REQUEST);
    }

    private static BusinessException notFound() {
        return new BusinessException("E-015", "対象のデータが見つかりません。", HttpStatus.NOT_FOUND);
    }

    private static BusinessException notSubmittable() {
        return new BusinessException("E-016", "申請が下書き・差戻し以外の状態であるため、この操作は行えません。",
                HttpStatus.CONFLICT);
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
