package com.example.kintaiflow.service;

import com.example.kintaiflow.dto.LeaveBalanceResponse;
import com.example.kintaiflow.dto.LeaveBalanceResponse.Grant;
import com.example.kintaiflow.dto.LeaveBalanceResponse.Item;
import com.example.kintaiflow.dto.LeaveBalanceResponse.Mandatory5;
import com.example.kintaiflow.dto.LeaveGrantAdjustRequest;
import com.example.kintaiflow.dto.LeaveGrantCreateRequest;
import com.example.kintaiflow.dto.LeaveGrantListResponse;
import com.example.kintaiflow.dto.LeaveGrantResponse;
import com.example.kintaiflow.dto.LeaveGrantRow;
import com.example.kintaiflow.entity.LeaveBalance;
import com.example.kintaiflow.entity.LeaveType;
import com.example.kintaiflow.entity.NotificationType;
import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.exception.BusinessException;
import com.example.kintaiflow.repository.LeaveBalanceRepository;
import com.example.kintaiflow.repository.LeaveTypeRepository;
import com.example.kintaiflow.repository.RequestRepository;
import com.example.kintaiflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ONL-013 休暇残日数取得 / ONL-017 休暇付与。
 * 残日数の判定・消化(FIFO)・戻し・付与の共通ロジックを提供する（申請/承認/バッチ向けの契約メソッドを含む）。
 */
@Service
@Transactional(readOnly = true)
public class LeaveBalanceService {

    private static final Logger log = LoggerFactory.getLogger(LeaveBalanceService.class);

    /** 年次有給休暇の種別名（ONL-015 が改名・無効化を禁止している）。 */
    static final String ANNUAL_LEAVE_NAME = "年次有給休暇";
    static final BigDecimal ZERO_1 = new BigDecimal("0.0");
    static final BigDecimal TEN = new BigDecimal("10");
    static final BigDecimal FIVE = new BigDecimal("5.0");
    private static final BigDecimal MIN_GRANT = new BigDecimal("0.1");
    private static final BigDecimal MAX_GRANT = new BigDecimal("99.9");
    private static final DateTimeFormatter MSG_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final int MESSAGE_MAX = 500;

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public LeaveBalanceService(LeaveBalanceRepository leaveBalanceRepository,
                               LeaveTypeRepository leaveTypeRepository,
                               UserRepository userRepository,
                               RequestRepository requestRepository,
                               NotificationService notificationService,
                               Clock clock) {
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ ONL-013

    /** 基準日 = 本日（JST）。 */
    public LeaveBalanceResponse getBalances(Long userId) {
        return getBalances(userId, LocalDate.now(clock));
    }

    /** 基準日時点で有効な付与行を休暇種別ごとに集計し、年5日進捗・次回付与予定日を返す。 */
    public LeaveBalanceResponse getBalances(Long userId, LocalDate asOf) {
        User user = userRepository.findById(userId).orElseThrow(LeaveBalanceService::notFound);   // ③
        List<LeaveBalance> rows = leaveBalanceRepository.findValidBalances(userId, asOf);         // ④
        LeaveType annual = leaveTypeRepository.findByName(ANNUAL_LEAVE_NAME).orElse(null);
        Long annualId = (annual == null) ? null : annual.getId();
        Map<Long, LeaveType> types = leaveTypeRepository.findAllById(
                        rows.stream().map(LeaveBalance::getLeaveTypeId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(LeaveType::getId, Function.identity()));
        Map<Long, List<LeaveBalance>> byType = rows.stream().collect(
                Collectors.groupingBy(LeaveBalance::getLeaveTypeId, LinkedHashMap::new, Collectors.toList())); // ⑤
        List<Item> items = new ArrayList<>();
        for (Map.Entry<Long, List<LeaveBalance>> e : byType.entrySet()) {
            items.add(toItem(types.get(e.getKey()), e.getValue(), annualId));
        }
        if (annual != null && !byType.containsKey(annualId)) {                                    // ⑥
            items.add(new Item(annualId, annual.getName(), true, ZERO_1, ZERO_1, ZERO_1, null, List.of()));
        }
        items.sort(Comparator.comparing((Item i) -> !i.annual()).thenComparing(Item::leaveTypeId));
        Mandatory5 m5 = calcMandatory5(userId, annualId, rows, asOf);                             // ⑦⑧⑨
        LocalDate next = calcNextGrantDate(user.getHireDate(), asOf);                             // ⑩
        return new LeaveBalanceResponse(asOf, items, m5, next);                                   // ⑪
    }

    private Item toItem(LeaveType type, List<LeaveBalance> rows, Long annualId) {
        List<Grant> grants = new ArrayList<>();
        BigDecimal granted = ZERO_1;
        BigDecimal used = ZERO_1;
        BigDecimal remaining = ZERO_1;
        LocalDate earliest = null;
        for (LeaveBalance b : rows) {
            BigDecimal rem = b.getGrantedDays().subtract(b.getUsedDays()).max(ZERO_1);   // 負の残は 0 に丸める
            if (b.getUsedDays().compareTo(b.getGrantedDays()) > 0) {
                log.warn("used_days exceeds granted_days: balanceId={}", b.getId());
            }
            grants.add(new Grant(b.getId(), b.getGrantedOn(), b.getExpiresOn(),
                    b.getGrantedDays(), b.getUsedDays(), rem));
            granted = granted.add(b.getGrantedDays());
            used = used.add(b.getUsedDays());
            remaining = remaining.add(rem);
            if (rem.signum() > 0 && (earliest == null || b.getExpiresOn().isBefore(earliest))) {
                earliest = b.getExpiresOn();
            }
        }
        Long typeId = type.getId();
        return new Item(typeId, type.getName(), typeId.equals(annualId), granted, used, remaining, earliest, grants);
    }

    /** 年5日取得義務の進捗（労基法39条7項）。 */
    private Mandatory5 calcMandatory5(Long userId, Long annualId, List<LeaveBalance> rows, LocalDate asOf) {
        if (annualId == null) return notTarget();
        Optional<LeaveBalance> base = rows.stream()
                .filter(b -> b.getLeaveTypeId().equals(annualId))
                .filter(b -> b.getGrantedDays().compareTo(TEN) >= 0)
                .filter(b -> asOf.isBefore(b.getGrantedOn().plusYears(1)))
                .max(Comparator.comparing(LeaveBalance::getGrantedOn));                           // ⑦
        if (base.isEmpty()) return notTarget();
        LocalDate start = base.get().getGrantedOn();
        LocalDate end = start.plusYears(1).minusDays(1);
        BigDecimal taken = requestRepository.sumApprovedLeaveDays(userId, annualId, start, end);  // ⑧
        if (taken == null) taken = ZERO_1;
        BigDecimal shortage = FIVE.subtract(taken).max(ZERO_1);                                   // ⑨
        return new Mandatory5(true, FIVE, taken, shortage, taken.compareTo(FIVE) >= 0,
                start, end, base.get().getGrantedDays());
    }

    private static Mandatory5 notTarget() {
        return new Mandatory5(false, FIVE, ZERO_1, ZERO_1, false, null, null, null);
    }

    /** 入社日+6か月を初回とし、1年ごとの応当日のうち基準日より後で最も早い日。 */
    static LocalDate calcNextGrantDate(LocalDate hireDate, LocalDate asOf) {
        LocalDate first = hireDate.plusMonths(6);
        long n = Math.max(0, ChronoUnit.YEARS.between(first, asOf));
        LocalDate next = first.plusYears(n);
        while (!next.isAfter(asOf)) {
            n++;
            next = first.plusYears(n);
        }
        return next;
    }

    /** BAT-002 用：基準日に年5日義務の対象期間内にある年休付与行（10日以上・ACTIVE ユーザー）。 */
    public List<LeaveBalance> findAlertBaseRows(LocalDate asOf) {
        LeaveType annual = leaveTypeRepository.findByName(ANNUAL_LEAVE_NAME).orElse(null);
        if (annual == null) return List.of();
        return leaveBalanceRepository.findAlertBaseRows(annual.getId(), TEN, asOf).stream()
                .filter(b -> asOf.isBefore(b.getGrantedOn().plusYears(1)))
                .toList();
    }

    /** 利用日に days を賄えるか。UNLIMITED は常に true。 */
    public boolean hasEnough(Long userId, Long leaveTypeId, BigDecimal days, LocalDate useDate) {
        requirePositive(days);
        LeaveType type = requireLeaveType(leaveTypeId);
        if (!"LIMITED".equals(type.getMaxDaysRule())) return true;
        return getRemainingDays(userId, leaveTypeId, useDate).compareTo(days) >= 0;
    }

    /** 利用日に有効な付与行の残日数合計。 */
    public BigDecimal getRemainingDays(Long userId, Long leaveTypeId, LocalDate useDate) {
        BigDecimal sum = leaveBalanceRepository.sumRemainingDays(userId, leaveTypeId, useDate);
        return (sum == null) ? ZERO_1 : sum.max(ZERO_1);
    }

    /** 最終承認時に失効日の早い付与行から順に消化する（FIFO）。不足は E-004。 */
    @Transactional
    public void consume(Long userId, Long leaveTypeId, BigDecimal days, LocalDate useDate) {
        requirePositive(days);
        LeaveType type = requireLeaveType(leaveTypeId);
        if (!"LIMITED".equals(type.getMaxDaysRule())) return;
        List<LeaveBalance> rows = leaveBalanceRepository.findConsumableForUpdate(userId, leaveTypeId, useDate);
        BigDecimal rest = days;
        for (LeaveBalance b : rows) {
            BigDecimal available = b.getGrantedDays().subtract(b.getUsedDays());
            BigDecimal take = available.min(rest);
            b.setUsedDays(b.getUsedDays().add(take));
            rest = rest.subtract(take);
            if (rest.signum() == 0) break;
        }
        if (rest.signum() > 0) {
            throw new BusinessException("E-004", "休暇残日数が不足しています。", HttpStatus.CONFLICT);
        }
        log.info("Leave consumed: userId={}, leaveTypeId={}, days={}, useDate={}", userId, leaveTypeId, days, useDate);
    }

    /** consume の逆操作。失効日の遅い行から戻す。 */
    @Transactional
    public void restore(Long userId, Long leaveTypeId, BigDecimal days, LocalDate useDate) {
        requirePositive(days);
        LeaveType type = requireLeaveType(leaveTypeId);
        if (!"LIMITED".equals(type.getMaxDaysRule())) return;
        List<LeaveBalance> rows = leaveBalanceRepository.findRestorableForUpdate(userId, leaveTypeId, useDate);
        BigDecimal rest = days;
        for (LeaveBalance b : rows) {
            BigDecimal give = b.getUsedDays().min(rest);
            b.setUsedDays(b.getUsedDays().subtract(give));
            rest = rest.subtract(give);
            if (rest.signum() == 0) break;
        }
        if (rest.signum() > 0) {
            throw new IllegalStateException("restore exceeds used_days: userId=" + userId
                    + ", leaveTypeId=" + leaveTypeId + ", rest=" + rest);
        }
        log.info("Leave restored: userId={}, leaveTypeId={}, days={}, useDate={}", userId, leaveTypeId, days, useDate);
    }

    /** 付与行を1件登録する（検証・重複確認・通知は呼出し側）。失効日 = 付与日+2年。 */
    @Transactional
    public LeaveBalance grant(Long userId, Long leaveTypeId, LocalDate grantedOn, BigDecimal days) {
        requirePositive(days);
        LeaveBalance b = new LeaveBalance();
        b.setUserId(userId);
        b.setLeaveTypeId(leaveTypeId);
        b.setGrantedDays(days.setScale(1, RoundingMode.HALF_UP));
        b.setUsedDays(ZERO_1);
        b.setGrantedOn(grantedOn);
        b.setExpiresOn(grantedOn.plusYears(2));
        return leaveBalanceRepository.save(b);
    }

    private LeaveType requireLeaveType(Long id) {
        return leaveTypeRepository.findById(id).orElseThrow(LeaveBalanceService::notFound);
    }

    private static void requirePositive(BigDecimal days) {
        if (days == null || days.signum() <= 0) {
            throw new IllegalArgumentException("days must be positive: " + days);
        }
    }

    // ------------------------------------------------------------------ ONL-017

    /** 付与履歴（種別名付き）と次回付与予定を返す。INACTIVE ユーザーも参照可。 */
    public LeaveGrantListResponse listGrants(Long userId, Long leaveTypeId) {
        if (userId == null) {
            throw new BusinessException("E-002", "従業員は必須入力です。", HttpStatus.BAD_REQUEST);
        }
        User user = userRepository.findById(userId).orElseThrow(LeaveBalanceService::notFound);
        LocalDate today = LocalDate.now(clock);
        List<LeaveGrantResponse> grants = leaveBalanceRepository.findGrantRows(userId, leaveTypeId).stream()
                .map(r -> toResponse(r, today)).toList();
        LocalDate next = calcNextGrantDate(user.getHireDate(), today);
        int count = regularGrantCount(user.getHireDate(), today);
        BigDecimal nextDays = statutoryDays(6L + 12L * count);
        return new LeaveGrantListResponse(user.getId(), user.getName(), user.getHireDate(), next, nextDays, grants);
    }

    /** 管理者による付与（users 行ロックで直列化）。付与対象へ通知する。 */
    @Transactional
    public LeaveGrantResponse grantManually(LeaveGrantCreateRequest req, Long adminId) {
        User user = userRepository.findByIdForUpdate(req.userId()).orElseThrow(LeaveBalanceService::notFound);
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("E-014", "従業員の指定が正しくありません。", HttpStatus.BAD_REQUEST);
        }
        LeaveType type = leaveTypeRepository.findById(req.leaveTypeId()).orElseThrow(LeaveBalanceService::notFound);
        if (!Boolean.TRUE.equals(type.getIsActive()) || !"LIMITED".equals(type.getMaxDaysRule())) {
            throw new BusinessException("E-014", "休暇種別の指定が正しくありません。", HttpStatus.BAD_REQUEST);
        }
        validateDays(req.grantedDays());
        LocalDate today = LocalDate.now(clock);
        validateGrantedOn(req.grantedOn(), user.getHireDate(), today);
        if (leaveBalanceRepository.existsByUserIdAndLeaveTypeIdAndGrantedOn(
                user.getId(), type.getId(), req.grantedOn())) {
            throw duplicate();
        }
        LeaveBalance lb;
        try {
            lb = grant(user.getId(), type.getId(), req.grantedOn(), req.grantedDays());
        } catch (DataIntegrityViolationException e) {
            throw duplicate();
        }
        notificationService.notify(user.getId(), NotificationType.LEAVE_GRANTED,
                buildGrantMessage(type.getName(), lb, req.note()), null);
        log.info("Leave grant created id={} userId={} typeId={} days={} by={}",
                lb.getId(), user.getId(), type.getId(), lb.getGrantedDays(), adminId);
        return toResponse(lb, type.getName(), today);
    }

    /** 付与日数の調整（上書き）。同値なら通知なしで成功。 */
    @Transactional
    public LeaveGrantResponse adjust(Long id, LeaveGrantAdjustRequest req, Long adminId) {
        validateDays(req.grantedDays());
        LeaveBalance lb = leaveBalanceRepository.findByIdForUpdate(id).orElseThrow(LeaveBalanceService::notFound);
        BigDecimal newDays = req.grantedDays().setScale(1, RoundingMode.HALF_UP);
        if (newDays.compareTo(lb.getUsedDays()) < 0) {
            throw new BusinessException("E-004", "休暇残日数が不足しています。", HttpStatus.CONFLICT);
        }
        String typeName = leaveTypeRepository.findById(lb.getLeaveTypeId()).map(LeaveType::getName).orElse("");
        BigDecimal before = lb.getGrantedDays();
        lb.setGrantedDays(newDays);   // dirty checking で UPDATE
        if (before.compareTo(newDays) != 0) {
            notificationService.notify(lb.getUserId(), NotificationType.LEAVE_GRANTED,
                    buildAdjustMessage(typeName, before, newDays, lb, req.note()), null);
        }
        log.info("Leave grant adjusted id={} userId={} {} -> {} by={}", id, lb.getUserId(), before, newDays, adminId);
        return toResponse(lb, typeName, LocalDate.now(clock));
    }

    private void validateDays(BigDecimal days) {
        if (days == null || days.compareTo(MIN_GRANT) < 0 || days.compareTo(MAX_GRANT) > 0
                || days.stripTrailingZeros().scale() > 1) {
            throw new BusinessException("E-003", "付与日数は0より大きい値（0.1〜99.9）で入力してください。",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void validateGrantedOn(LocalDate grantedOn, LocalDate hireDate, LocalDate today) {
        if (grantedOn.isBefore(hireDate) || grantedOn.isAfter(today) || grantedOn.plusYears(2).isBefore(today)) {
            throw new BusinessException("E-014", "付与日の指定が正しくありません。", HttpStatus.BAD_REQUEST);
        }
    }

    /** 勤続月数に応じた法定付与日数（週所定5日以上・フルタイム想定）。6か月未満は 0。 */
    static BigDecimal statutoryDays(long months) {
        int d;
        if (months < 6) d = 0;
        else if (months < 18) d = 10;
        else if (months < 30) d = 11;
        else if (months < 42) d = 12;
        else if (months < 54) d = 14;
        else if (months < 66) d = 16;
        else if (months < 78) d = 18;
        else d = 20;
        return new BigDecimal(d).setScale(1);
    }

    /** 基準日までに到来した定期付与（入社6か月後が第1回、以降1年ごと）の回数。 */
    static int regularGrantCount(LocalDate hireDate, LocalDate asOf) {
        LocalDate first = hireDate.plusMonths(6);
        int n = 0;
        while (!first.plusYears(n).isAfter(asOf)) n++;
        return n;
    }

    private LeaveGrantResponse toResponse(LeaveGrantRow r, LocalDate today) {
        return new LeaveGrantResponse(r.id(), r.leaveTypeId(), r.leaveTypeName(), r.grantedOn(),
                r.grantedDays(), r.usedDays(), r.grantedDays().subtract(r.usedDays()),
                r.expiresOn(), r.expiresOn().isBefore(today));
    }

    private LeaveGrantResponse toResponse(LeaveBalance lb, String typeName, LocalDate today) {
        return new LeaveGrantResponse(lb.getId(), lb.getLeaveTypeId(), typeName, lb.getGrantedOn(),
                lb.getGrantedDays(), lb.getUsedDays(), lb.getGrantedDays().subtract(lb.getUsedDays()),
                lb.getExpiresOn(), lb.getExpiresOn().isBefore(today));
    }

    String buildGrantMessage(String typeName, LeaveBalance lb, String note) {
        String msg = String.format("%sが%s日付与されました（付与日 %s、失効日 %s）。", typeName, lb.getGrantedDays(),
                lb.getGrantedOn().format(MSG_DATE), lb.getExpiresOn().format(MSG_DATE));
        return withNote(msg, note);
    }

    String buildAdjustMessage(String typeName, BigDecimal before, BigDecimal after, LeaveBalance lb, String note) {
        String msg = String.format("%sの付与日数が%s日から%s日に調整されました（付与日 %s、失効日 %s）。", typeName, before,
                after, lb.getGrantedOn().format(MSG_DATE), lb.getExpiresOn().format(MSG_DATE));
        return withNote(msg, note);
    }

    private static String withNote(String msg, String note) {
        if (note != null && !note.isBlank()) msg = msg + " 理由：" + note;
        return msg.length() > MESSAGE_MAX ? msg.substring(0, MESSAGE_MAX) : msg;
    }

    private static BusinessException notFound() {
        return new BusinessException("E-015", "対象のデータが見つかりません。", HttpStatus.NOT_FOUND);
    }

    private static BusinessException duplicate() {
        return new BusinessException("E-013", "付与は既に登録されています。", HttpStatus.CONFLICT);
    }
}
