package com.example.kintaiflow.repository;

import com.example.kintaiflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** メールアドレスでユーザーを検索する（ログインで使用）。 */
    Optional<User> findByEmail(String email);
}
