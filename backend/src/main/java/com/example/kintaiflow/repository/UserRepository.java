package com.example.kintaiflow.repository;

import com.example.kintaiflow.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** メールアドレスでユーザーを検索する（ログインで使用）。email は正規化済みの値を渡す。 */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByManagerIdAndStatus(Long managerId, String status);

    List<User> findByRoleAndStatus(String role, String status);

    /** 行ロック付き。必ずトランザクション内で使う。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Query("select u from User u where (lower(u.name) like lower(concat('%', :keyword, '%')) escape '\\' or lower(u.email) like lower(concat('%', :keyword, '%')) escape '\\') and (:role = '' or u.role = :role) and (:status = '' or u.status = :status) and (:departmentId = 0L or u.departmentId = :departmentId) order by u.id")
    List<User> search(@Param("keyword") String keyword, @Param("role") String role, @Param("status") String status, @Param("departmentId") Long departmentId);
}
