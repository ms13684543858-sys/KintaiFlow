package com.example.kintaiflow.repository;

import com.example.kintaiflow.entity.Request;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<Request, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Request r WHERE r.id = :id")
    Optional<Request> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT r FROM Request r WHERE r.userId = :userId ORDER BY COALESCE(r.submittedAt, r.createdAt) DESC, r.id DESC")
    List<Request> findAllByUser(@Param("userId") Long userId);

    @Query("SELECT r FROM Request r WHERE r.userId = :userId AND r.status = :status ORDER BY COALESCE(r.submittedAt, r.createdAt) DESC, r.id DESC")
    List<Request> findAllByUserAndStatus(@Param("userId") Long userId, @Param("status") String status);

    /** 期間が重なる有効な休暇申請（statuses は RequestStatus.BLOCKING を渡す）。 */
    @Query("SELECT r FROM Request r WHERE r.userId = :userId AND r.requestType = 'LEAVE' AND r.status IN :statuses "
            + "AND r.startDate <= :to AND r.endDate >= :from")
    List<Request> findOverlappingLeaves(@Param("userId") Long userId, @Param("from") LocalDate from,
                                        @Param("to") LocalDate to, @Param("statuses") Collection<String> statuses);

    /** 承認待ち（PENDING）の休暇申請日数の合計（残日数の引当）。該当なしは null。 */
    @Query("SELECT SUM(r.days) FROM Request r WHERE r.userId = :userId AND r.requestType = 'LEAVE' "
            + "AND r.leaveTypeId = :leaveTypeId AND r.status = 'PENDING'")
    BigDecimal sumPendingLeaveDays(@Param("userId") Long userId, @Param("leaveTypeId") Long leaveTypeId);

    @Query("select sum(r.days) from Request r where r.userId = :userId and r.requestType = 'LEAVE' and r.leaveTypeId = :leaveTypeId and r.status = 'APPROVED' and r.startDate between :from and :to")
    BigDecimal sumApprovedLeaveDays(@Param("userId") Long userId, @Param("leaveTypeId") Long leaveTypeId,
                                    @Param("from") LocalDate from, @Param("to") LocalDate to);

    boolean existsByUserIdAndRequestTypeAndStartDateAndStatus(Long userId, String requestType, LocalDate startDate, String status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE requests SET status = :newStatus WHERE id = :id AND status = 'PENDING' AND current_step = :step")
    int updateStatusIfPending(@Param("id") Long id, @Param("step") int step, @Param("newStatus") String newStatus);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE requests SET current_step = :toStep WHERE id = :id AND status = 'PENDING' AND current_step = :fromStep")
    int advanceStepIfPending(@Param("id") Long id, @Param("fromStep") int fromStep, @Param("toStep") int toStep);
}
