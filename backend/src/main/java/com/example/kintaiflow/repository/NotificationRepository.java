package com.example.kintaiflow.repository;

import com.example.kintaiflow.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE notifications SET is_read = TRUE WHERE id = :id AND user_id = :userId AND is_read = FALSE")
    int markAsRead(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByUserIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId, String type, LocalDateTime from, LocalDateTime to);

    boolean existsByUserIdAndTypeAndMessageStartingWithAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId, String type, String messagePrefix, LocalDateTime from, LocalDateTime to);
}
