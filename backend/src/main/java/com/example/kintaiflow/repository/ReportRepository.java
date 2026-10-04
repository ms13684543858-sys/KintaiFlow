package com.example.kintaiflow.repository;

import com.example.kintaiflow.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/** 月次集計の native SQL（ONL-018 / ONL-020 共用）。CRUD を公開しないため Repository を継承する。 */
public interface ReportRepository extends Repository<User, Long> {

    /**
     * 対象期間の社員別集計。列順: 0:id 1:name 2:department_name 3:work_days 4:work_minutes 5:overtime_minutes 6:leave_days。
     * departmentId / managerId は null で絞込無効。
     */
    @Query(nativeQuery = true, value = """
            SELECT u.id,
                   u.name,
                   d.name AS department_name,
                   COALESCE(a.work_days, 0)        AS work_days,
                   COALESCE(a.work_minutes, 0)     AS work_minutes,
                   COALESCE(a.overtime_minutes, 0) AS overtime_minutes,
                   COALESCE(l.leave_days, 0)       AS leave_days
            FROM users u
            LEFT JOIN departments d ON d.id = u.department_id
            LEFT JOIN (SELECT user_id,
                              COUNT(*)              AS work_days,
                              SUM(work_minutes)     AS work_minutes,
                              SUM(overtime_minutes) AS overtime_minutes
                       FROM attendance_records
                       WHERE work_date BETWEEN :fromDate AND :toDate
                         AND clock_in IS NOT NULL
                       GROUP BY user_id) a ON a.user_id = u.id
            LEFT JOIN (SELECT user_id, SUM(days) AS leave_days
                       FROM requests
                       WHERE request_type = 'LEAVE' AND status = 'APPROVED'
                         AND start_date BETWEEN :fromDate AND :toDate
                       GROUP BY user_id) l ON l.user_id = u.id
            WHERE u.hire_date <= :toDate
              AND (u.status = 'ACTIVE' OR a.user_id IS NOT NULL OR l.user_id IS NOT NULL)
              AND (CAST(:departmentId AS BIGINT) IS NULL OR u.department_id = :departmentId)
              AND (CAST(:managerId AS BIGINT) IS NULL OR u.manager_id = :managerId)
            ORDER BY CASE WHEN u.department_id IS NULL THEN 1 ELSE 0 END, u.department_id, u.id
            """)
    List<Object[]> findMonthlySummary(@Param("fromDate") LocalDate fromDate,
                                      @Param("toDate") LocalDate toDate,
                                      @Param("departmentId") Long departmentId,
                                      @Param("managerId") Long managerId);
}
