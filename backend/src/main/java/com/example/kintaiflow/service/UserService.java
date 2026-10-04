package com.example.kintaiflow.service;

import com.example.kintaiflow.dto.*;
import com.example.kintaiflow.entity.Department;
import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.exception.BusinessException;
import com.example.kintaiflow.repository.DepartmentRepository;
import com.example.kintaiflow.repository.UserRepository;
import com.example.kintaiflow.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** ONL-014 ユーザー管理（一覧・詳細・登録・変更/無効化・パスワード設定）。 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final Set<String> ROLES = Set.of("EMPLOYEE", "MANAGER", "ADMIN");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final int MAX_CHAIN_DEPTH = 100;

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, DepartmentRepository departmentRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 条件に合うユーザーを id 昇順で全件返す。 */
    public UserListResponse search(String keyword, String role, String status, Long departmentId) {
        String kw = (keyword == null) ? "" : EmailNormalizer.normalize(keyword);
        if (kw.length() > 100) throw badRequest("検索キーワードは100文字以内で入力してください。");
        String r = blankToEmpty(role);
        String s = blankToEmpty(status);
        if (!r.isEmpty() && !ROLES.contains(r)) throw badRequest("ロールの指定が正しくありません。");
        if (!s.isEmpty() && !STATUSES.contains(s)) throw badRequest("状態の指定が正しくありません。");
        List<User> users = userRepository.search(escapeLike(kw), r, s, departmentId == null ? 0L : departmentId);
        List<UserResponse> list = toResponses(users);
        return new UserListResponse(list, list.size());
    }

    public UserDetailResponse get(Long id) {
        User u = userRepository.findById(id).orElseThrow(UserService::notFound);
        return new UserDetailResponse(toResponses(List.of(u)).get(0));
    }

    @Transactional
    public UserDetailResponse create(UserCreateRequest req, Long actorId) {
        String email = EmailNormalizer.normalize(req.email());
        if (userRepository.existsByEmail(email)) throw duplicated("メールアドレス");
        validateDepartment(req.departmentId());
        validateManager(null, req.managerId());
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(req.initialPassword()));
        u.setName(req.name().strip());
        u.setRole(req.role());
        u.setDepartmentId(req.departmentId());
        u.setManagerId(req.managerId());
        u.setHireDate(req.hireDate());
        u.setStatus("ACTIVE");
        u.setMustChangePassword(true); // 初期パスワードは初回ログイン時に変更を強制する
        try {
            u = userRepository.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            throw duplicated("メールアドレス");
        }
        log.info("User created: id={}, role={}, actorId={}", u.getId(), u.getRole(), actorId);
        return new UserDetailResponse(toResponses(List.of(u)).get(0));
    }

    @Transactional
    public UserDetailResponse update(Long id, UserUpdateRequest req, Long actorId) {
        User u = userRepository.findByIdForUpdate(id).orElseThrow(UserService::notFound);
        boolean self = id.equals(actorId);
        if (self && ("INACTIVE".equals(req.status()) || !"ADMIN".equals(req.role()))) {
            throw new BusinessException("E-010", "この操作を行う権限がありません。", HttpStatus.FORBIDDEN);
        }
        String email = EmailNormalizer.normalize(req.email());
        if (userRepository.existsByEmailAndIdNot(email, id)) throw duplicated("メールアドレス");
        validateDepartment(req.departmentId());
        validateManager(id, req.managerId());
        boolean deactivating = "INACTIVE".equals(req.status()) && !"INACTIVE".equals(u.getStatus());
        boolean demoting = "EMPLOYEE".equals(req.role()) && !"EMPLOYEE".equals(u.getRole());
        if ((deactivating || demoting) && userRepository.existsByManagerIdAndStatus(id, "ACTIVE")) {
            throw new BusinessException("E-016",
                    "有効な部下が設定されているため、この操作は行えません。", HttpStatus.CONFLICT);
        }
        u.setEmail(email);
        u.setName(req.name().strip());
        u.setRole(req.role());
        u.setDepartmentId(req.departmentId());
        u.setManagerId(req.managerId());
        u.setHireDate(req.hireDate());
        u.setStatus(req.status());
        try {
            u = userRepository.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            throw duplicated("メールアドレス");
        }
        log.info("User updated: id={}, status={}, role={}, actorId={}", id, u.getStatus(), u.getRole(), actorId);
        return new UserDetailResponse(toResponses(List.of(u)).get(0));
    }

    @Transactional
    public UserDetailResponse resetPassword(Long id, PasswordResetRequest req, Long actorId) {
        User u = userRepository.findByIdForUpdate(id).orElseThrow(UserService::notFound);
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        u.setMustChangePassword(true); // 管理者が設定したパスワードは本人が変更するまで暫定扱い
        u.setFailedLoginCount(0);      // ロック解除も兼ねる
        u.setLockedUntil(null);
        userRepository.saveAndFlush(u);
        log.info("User password reset: id={}, actorId={}", id, actorId);
        return new UserDetailResponse(toResponses(List.of(u)).get(0));
    }

    private void validateDepartment(Long departmentId) {
        if (departmentId != null && !departmentRepository.existsById(departmentId)) {
            throw invalid("所属");
        }
    }

    private void validateManager(Long targetId, Long managerId) {
        if (managerId == null) return;
        if (managerId.equals(targetId)) throw invalid("上長");
        User m = userRepository.findById(managerId).orElseThrow(() -> invalid("上長"));
        if (!"ACTIVE".equals(m.getStatus())
                || !("MANAGER".equals(m.getRole()) || "ADMIN".equals(m.getRole()))) {
            throw invalid("上長");
        }
        if (targetId == null) return;
        Long cur = m.getManagerId();
        int depth = 0;
        while (cur != null) {
            if (cur.equals(targetId) || ++depth > MAX_CHAIN_DEPTH) {
                log.warn("Manager chain cycle: targetId={}, managerId={}", targetId, managerId);
                throw invalid("上長");
            }
            cur = userRepository.findById(cur).map(User::getManagerId).orElse(null);
        }
    }

    /** 所属名・上長名を一括取得して応答に変換する（N+1 回避）。 */
    private List<UserResponse> toResponses(List<User> users) {
        Map<Long, String> deptNames = departmentRepository.findAllById(
                        users.stream().map(User::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Department::getId, Department::getName));
        Map<Long, String> mgrNames = userRepository.findAllById(
                        users.stream().map(User::getManagerId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::getName));
        return users.stream().map(u -> new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(),
                u.getDepartmentId(), u.getDepartmentId() == null ? null : deptNames.get(u.getDepartmentId()),
                u.getManagerId(), u.getManagerId() == null ? null : mgrNames.get(u.getManagerId()),
                u.getHireDate() == null ? null : u.getHireDate().toString(), u.getStatus())).toList();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static BusinessException notFound() {
        return new BusinessException("E-015", "対象のデータが見つかりません。", HttpStatus.NOT_FOUND);
    }

    private static BusinessException duplicated(String item) {
        return new BusinessException("E-013", item + "は既に登録されています。", HttpStatus.CONFLICT);
    }

    private static BusinessException invalid(String item) {
        return new BusinessException("E-014", item + "の指定が正しくありません。", HttpStatus.BAD_REQUEST);
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException("E-002", message, HttpStatus.BAD_REQUEST);
    }

    private static String blankToEmpty(String s) {
        return (s == null || s.isBlank()) ? "" : s.strip();
    }
}
