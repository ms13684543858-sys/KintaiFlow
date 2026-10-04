package com.example.kintaiflow.config;

import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.repository.UserRepository;
import com.example.kintaiflow.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;

/**
 * 本番の初回起動用：users が空で、環境変数（KINTAIFLOW_BOOTSTRAP_ADMIN_EMAIL / _PASSWORD）が
 * 指定されている場合だけ、最初の管理者を1名作る。初回ログイン時にパスワード変更を必須にする。
 * パスワードがポリシー(12文字以上・英数字)を満たさない場合は、起動を失敗させる（弱い管理者を作らない）。
 * 開発用ダミーユーザー(DevDataInitializer)が先に作られた場合は何もしない。
 */
@Component
@Order(2)
public class BootstrapAdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public BootstrapAdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                     @Value("${kintaiflow.bootstrap.admin-email:}") String email,
                                     @Value("${kintaiflow.bootstrap.admin-password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (email.isBlank() || password.isBlank() || userRepository.count() > 0) {
            return;
        }
        String problem = AuthService.validatePolicy(password, "");
        if (problem != null) {
            throw new IllegalStateException("初期管理者のパスワードがポリシーを満たしません: " + problem);
        }
        User u = new User();
        u.setEmail(email.trim().toLowerCase(Locale.ROOT));
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setName("システム管理者");
        u.setRole("ADMIN");
        u.setDepartmentId(1L);
        u.setHireDate(LocalDate.now());
        u.setStatus("ACTIVE");
        u.setMustChangePassword(true);
        userRepository.save(u);
        log.info("Bootstrap admin created: {}", u.getEmail());
    }
}
