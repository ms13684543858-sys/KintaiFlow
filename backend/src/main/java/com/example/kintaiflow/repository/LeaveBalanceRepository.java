package com.example.kintaiflow.repository;

import com.example.kintaiflow.dto.LeaveGrantRow;
import com.example.kintaiflow.entity.LeaveBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    /** 基準日に有効（付与日 ≦ 基準日 ≦ 失効日）な残の行を、種別・失効日順で返す。 */
    @Query("select b from LeaveBalance b where b.userId = :userId and b.grantedOn <= :asOf and b.expiresOn >= :asOf order by b.leaveTypeId, b.expiresOn, b.grantedOn, b.id")
    List<LeaveBalance> findValidBalances(@Param("userId") Long userId, @Param("asOf") LocalDate asOf);

    /** 基準日に有効な残日数の合計（付与−使用）。該当なしは null。 */
    @Query("select sum(b.grantedDays - b.usedDays) from LeaveBalance b where b.userId = :userId and b.leaveTypeId = :leaveTypeId and b.grantedOn <= :useDate and b.expiresOn >= :useDate")
    BigDecimal sumRemainingDays(@Param("userId") Long userId, @Param("leaveTypeId") Long leaveTypeId, @Param("useDate") LocalDate useDate);

    /** 消化対象（失効日が近い順）。必ずトランザクション内で呼ぶ。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from LeaveBalance b where b.userId = :userId and b.leaveTypeId = :leaveTypeId and b.grantedOn <= :useDate and b.expiresOn >= :useDate and b.usedDays < b.grantedDays order by b.expiresOn, b.grantedOn, b.id")
    List<LeaveBalance> findConsumableForUpdate(@Param("userId") Long userId, @Param("leaveTypeId") Long leaveTypeId, @Param("useDate") LocalDate useDate);

    /** 戻し対象（失効日が遠い順）。必ずトランザクション内で呼ぶ。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from LeaveBalance b where b.userId = :userId and b.leaveTypeId = :leaveTypeId and b.grantedOn <= :useDate and b.expiresOn >= :useDate and b.usedDays > 0 order by b.expiresOn desc, b.grantedOn desc, b.id desc")
    List<LeaveBalance> findRestorableForUpdate(@Param("userId") Long userId, @Param("leaveTypeId") Long leaveTypeId, @Param("useDate") LocalDate useDate);

    @Query("select b from LeaveBalance b, User u where u.id = b.userId and u.status = 'ACTIVE' and b.leaveTypeId = :leaveTypeId and b.grantedDays >= :minDays and b.grantedOn <= :asOf and b.expiresOn >= :asOf order by b.userId, b.grantedOn desc, b.id desc")
    List<LeaveBalance> findAlertBaseRows(@Param("leaveTypeId") Long leaveTypeId, @Param("minDays") BigDecimal minDays, @Param("asOf") LocalDate asOf);

    /** 付与履歴（leaveTypeId が null なら全種別）。PostgreSQL で「:param IS NULL」が型不明になるのを避けるため 2 本に分けている。 */
    default List<LeaveGrantRow> findGrantRows(Long userId, Long leaveTypeId) {
        return leaveTypeId == null ? findGrantRowsAll(userId) : findGrantRowsByType(userId, leaveTypeId);
    }

    @Query("SELECT new com.example.kintaiflow.dto.LeaveGrantRow(lb.id, lb.leaveTypeId, lt.name, lb.grantedOn, lb.grantedDays, lb.usedDays, lb.expiresOn) FROM LeaveBalance lb JOIN LeaveType lt ON lt.id = lb.leaveTypeId WHERE lb.userId = :userId ORDER BY lb.grantedOn DESC, lb.id DESC")
    List<LeaveGrantRow> findGrantRowsAll(@Param("userId") Long userId);

    @Query("SELECT new com.example.kintaiflow.dto.LeaveGrantRow(lb.id, lb.leaveTypeId, lt.name, lb.grantedOn, lb.grantedDays, lb.usedDays, lb.expiresOn) FROM LeaveBalance lb JOIN LeaveType lt ON lt.id = lb.leaveTypeId WHERE lb.userId = :userId AND lb.leaveTypeId = :leaveTypeId ORDER BY lb.grantedOn DESC, lb.id DESC")
    List<LeaveGrantRow> findGrantRowsByType(@Param("userId") Long userId, @Param("leaveTypeId") Long leaveTypeId);

    boolean existsByUserIdAndLeaveTypeIdAndGrantedOn(Long userId, Long leaveTypeId, LocalDate grantedOn);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lb FROM LeaveBalance lb WHERE lb.id = :id")
    Optional<LeaveBalance> findByIdForUpdate(@Param("id") Long id);
}
