package com.example.kintaiflow.service;

import com.example.kintaiflow.dto.ChangePasswordRequest;
import com.example.kintaiflow.dto.LoginRequest;
import com.example.kintaiflow.dto.LoginResponse;
import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.exception.BusinessException;
import com.example.kintaiflow.repository.UserRepository;
import com.example.kintaiflow.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

/** ONL-001 ログイン（ロック機能付き）／ ONL-023 パスワード変更。 */
@Service
public class AuthService {

    /** この回数だけ連続で失敗するとロックする */
    static final int MAX_FAILED_ATTEMPTS = 5;
    /** ロック時間（分） */
    static final int LOCK_MINUTES = 30;
    static final int MIN_PASSWORD_LENGTH = 12;
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * 失敗回数の記録（UPDATE）を、エラーを投げた後も確定させたいので noRollbackFor を指定する。
     * （RuntimeException の既定動作はロールバックのため、指定しないと失敗回数が保存されない）
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request) {
        // 1. メールアドレスを正規化（前後の空白除去・小文字化）して検索
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(email).orElseThrow(AuthService::invalidCredentials);
        LocalDateTime now = LocalDateTime.now(JST);

        // 2. 無効ユーザーは「ユーザーが存在しない」と同じ応答にする
        if (!"ACTIVE".equals(user.getStatus())) {
            throw invalidCredentials();
        }
        // 3. ロック中なら、パスワードの正否にかかわらず拒否する
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new BusinessException("E-017",
                    "アカウントがロックされています。しばらくしてから再度お試しください。", HttpStatus.LOCKED);
        }
        // 4. パスワード照合。失敗したら回数を増やし、上限に達したらロックする
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int count = user.getFailedLoginCount() + 1;
            if (count >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(now.plusMinutes(LOCK_MINUTES));
                user.setFailedLoginCount(0);
            } else {
                user.setFailedLoginCount(count);
            }
            userRepository.save(user);
            throw invalidCredentials();
        }
        // 5. 成功：失敗回数とロックを解除
        if (user.getFailedLoginCount() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
        return new LoginResponse(jwtService.issue(user), user.getId(), user.getName(), user.getRole(),
                user.isMustChangePassword());
    }

    /** ONL-023 ログイン中のユーザー自身のパスワードを変更する。 */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("E-015", "対象のデータが見つかりません。", HttpStatus.NOT_FOUND));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("E-018", "現在のパスワードが正しくありません。", HttpStatus.BAD_REQUEST);
        }
        String problem = validatePolicy(request.newPassword(), request.currentPassword());
        if (problem != null) {
            throw new BusinessException("E-002", problem, HttpStatus.BAD_REQUEST);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(LocalDateTime.now(JST));
        userRepository.save(user);
    }

    /** パスワードポリシー（KF-RD-002 §4）。問題が無ければ null、あればメッセージを返す。 */
    static String validatePolicy(String newPassword, String currentPassword) {
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            return "新しいパスワードは" + MIN_PASSWORD_LENGTH + "文字以上で入力してください。";
        }
        boolean hasLetter = newPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = newPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            return "新しいパスワードは英字と数字を両方含めてください。";
        }
        if (newPassword.equals(currentPassword)) {
            return "新しいパスワードは現在のパスワードと異なるものにしてください。";
        }
        return null;
    }

    /** ユーザーが存在しない場合もパスワード違いの場合も同じメッセージにする（E-001）。 */
    private static BusinessException invalidCredentials() {
        return new BusinessException("E-001", "メールアドレスまたはパスワードが正しくありません。", HttpStatus.UNAUTHORIZED);
    }
}
