package com.example.kintaiflow.repository;

import com.example.kintaiflow.entity.AttendanceRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    /** 行ロック付き（SELECT ... FOR UPDATE）。トランザクション内でのみ使う。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AttendanceRecord> findWithLockByUserIdAndWorkDate(Long userId, LocalDate workDate);

    List<AttendanceRecord> findByUserIdAndWorkDateBetweenOrderByWorkDateAsc(Long userId, LocalDate from, LocalDate to);
}
