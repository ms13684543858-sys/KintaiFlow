package com.example.kintaiflow.repository;

import com.example.kintaiflow.entity.AttendanceBreak;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceBreakRepository extends JpaRepository<AttendanceBreak, Long> {

    List<AttendanceBreak> findByAttendanceRecordIdOrderByStartAtAsc(Long attendanceRecordId);

    List<AttendanceBreak> findByAttendanceRecordIdInOrderByStartAtAsc(Collection<Long> attendanceRecordIds);

    /** 終了していない区間（休憩中／離席中）。同時に高々1件（部分ユニーク索引 uq_attendance_breaks_open）。 */
    Optional<AttendanceBreak> findFirstByAttendanceRecordIdAndEndAtIsNull(Long attendanceRecordId);
}
