package com.example.kintaiflow.repository;

import com.example.kintaiflow.dto.PendingApprovalRow;
import com.example.kintaiflow.entity.ApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {

    List<ApprovalStep> findByRequestIdOrderByStepNo(Long requestId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ApprovalStep s WHERE s.requestId = :requestId")
    int deleteByRequestId(@Param("requestId") Long requestId);

    /** WAITING の場合だけ処理済みにする（同時操作の二重承認を防ぐ）。更新行数 0 なら既に処理済み。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE approval_steps SET status = :status, approver_id = :approverId, comment = CAST(:comment AS VARCHAR(500)), acted_at = :now WHERE id = :id AND status = 'WAITING'")
    int actIfWaiting(@Param("id") Long id, @Param("status") String status, @Param("approverId") Long approverId,
                     @Param("comment") String comment, @Param("now") LocalDateTime now);

    @Query("""
      SELECT new com.example.kintaiflow.dto.PendingApprovalRow(
             s.id, s.stepNo, r.id, r.requestType, u.id, u.name, d.name,
             r.startDate, r.endDate, r.days, r.submittedAt, s1.actedAt)
        FROM ApprovalStep s
        JOIN Request r ON r.id = s.requestId
        JOIN User u ON u.id = r.userId
        LEFT JOIN Department d ON d.id = u.departmentId
        LEFT JOIN ApprovalStep s1 ON s1.requestId = r.id AND s1.stepNo = 1 AND s1.status = 'APPROVED'
       WHERE s.status = 'WAITING'
         AND r.status = 'PENDING'
         AND r.currentStep = s.stepNo
         AND r.userId <> :userId
         AND ((s.stepNo = 1 AND s.approverId = :userId) OR (s.stepNo = 2 AND :admin = true))
         AND s.stepNo IN :stepNos
         AND r.requestType IN :types
       ORDER BY r.submittedAt ASC, s.id ASC
      """)
    List<PendingApprovalRow> findPending(@Param("userId") Long userId, @Param("admin") boolean admin,
                                         @Param("types") List<String> types, @Param("stepNos") List<Integer> stepNos);
}
