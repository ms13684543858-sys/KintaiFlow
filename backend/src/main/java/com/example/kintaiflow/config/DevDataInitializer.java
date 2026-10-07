package com.example.kintaiflow.config;

import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 開発用：users テーブルが空のときだけ、架空のユーザー100名（管理部10＋技術者90）を作る。
 * 初期パスワードは application-local.properties の kintaiflow.dev.seed-password（全員同じ）。未設定なら何もしない。
 * 部署ID（schema.sql の初期データ）：1=管理部、2=システム開発事業部、3=ソリューション事業部、4=SAP事業部、5=クラウド基盤サービス部。
 * 代表アカウント：admin@（管理者）、manager@（上長）、taro@・hanako@（社員）。他は <接頭辞><3桁>@example.com。
 * 本番環境では使用しない（seed-password を設定しなければ動かない）。
 */
@ConditionalOnProperty(name = "kintaiflow.dev.seed-enabled", havingValue = "true") // 開発用：明示的に有効化した環境でのみ動く（本番では未設定）
@Component
@Order(1)
public class DevDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    private static final String[] SURNAMES = {"佐藤", "鈴木", "高橋", "田中", "伊藤", "渡辺", "山本", "中村", "小林", "加藤",
            "吉田", "山田", "佐々木", "山口", "松本", "井上", "木村", "林", "斎藤", "清水"};
    private static final String[] GIVEN = {"太郎", "花子", "一郎", "美咲", "健太", "陽子", "大輔", "結衣", "翔太", "恵",
            "拓也", "直美", "優太", "愛", "亮", "千尋", "誠", "由美", "剛", "さくら"};

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedPassword;
    /** 部下 → 上長（保存前は上長の ID が無いため、参照で覚えておく） */
    private final Map<User, User> managerOf = new IdentityHashMap<>();

    public DevDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                              @Value("${kintaiflow.dev.seed-password:}") String seedPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(String... args) {
        if (seedPassword.isBlank() || userRepository.count() > 0) {
            return;
        }
        String hash = passwordEncoder.encode(seedPassword);   // 全員同じパスワードなので、ハッシュは1回だけ計算する
        List<User> all = new ArrayList<>();
        int seq = 0;

        // 管理部（10名）：管理者2、上長1（管理部長）、社員7
        add(all, hash, "admin@example.com", "人事 管理者", "ADMIN", 1L, null, seq++);
        add(all, hash, "adm002@example.com", name(seq), "ADMIN", 1L, null, seq++);
        User adminMgr = add(all, hash, "adm003@example.com", name(seq), "MANAGER", 1L, null, seq++);
        for (int i = 4; i <= 10; i++) {
            add(all, hash, String.format("adm%03d@example.com", i), name(seq), "EMPLOYEE", 1L, adminMgr, seq++);
        }

        // 技術部門（90名）：部署ごとに 上長2名 ＋ 社員
        seq = addDepartment(all, hash, 2L, "dev", 30, seq, true);
        seq = addDepartment(all, hash, 3L, "sol", 25, seq, false);
        seq = addDepartment(all, hash, 4L, "sap", 20, seq, false);
        addDepartment(all, hash, 5L, "cld", 15, seq, false);

        // 上長を先に保存して ID を確定させてから、部下を保存する（manager_id の外部キーのため）
        List<User> top = all.stream().filter(u -> !managerOf.containsKey(u)).toList();
        userRepository.saveAll(top);
        List<User> staff = all.stream().filter(managerOf::containsKey).toList();
        staff.forEach(u -> u.setManagerId(managerOf.get(u).getId()));
        userRepository.saveAll(staff);
        log.info("Dev seed users created ({} users).", all.size());
    }

    /** 部署の人員を作る。先頭2名が上長。withSamples=true の部署には代表アカウント(manager/taro/hanako)を含める。 */
    private int addDepartment(List<User> all, String hash, Long deptId, String prefix, int size, int seq, boolean withSamples) {
        String m1 = withSamples ? "manager@example.com" : prefix + "001@example.com";
        User mgr1 = add(all, hash, m1, withSamples ? "佐藤 一郎" : name(seq), "MANAGER", deptId, null, seq++);
        User mgr2 = add(all, hash, prefix + "002@example.com", name(seq), "MANAGER", deptId, null, seq++);
        int n = 3;
        if (withSamples) {
            add(all, hash, "taro@example.com", "山田 太郎", "EMPLOYEE", deptId, mgr1, seq++);
            add(all, hash, "hanako@example.com", "鈴木 花子", "EMPLOYEE", deptId, mgr1, seq++);
            n = 5;
        }
        for (; n <= size; n++) {
            User mgr = (n % 2 == 0) ? mgr1 : mgr2;
            add(all, hash, String.format("%s%03d@example.com", prefix, n), name(seq), "EMPLOYEE", deptId, mgr, seq++);
        }
        return seq;
    }

    private static String name(int seq) {
        return SURNAMES[seq % SURNAMES.length] + " " + GIVEN[(seq * 7 + 3) % GIVEN.length];
    }

    /** ユーザーを組み立てて一覧に加える（保存はまとめて run() で行う）。 */
    private User add(List<User> all, String hash, String email, String name, String role, Long deptId, User manager, int seq) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(hash);
        u.setName(name);
        u.setRole(role);
        u.setDepartmentId(deptId);
        u.setHireDate(LocalDate.of(2016, 4, 1).plusDays((seq * 97L) % 3500));
        u.setStatus("ACTIVE");
        if (manager != null) {
            managerOf.put(u, manager);
        }
        all.add(u);
        return u;
    }
}
