package com.example.kintaiflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** leave_balances テーブル（休暇残：付与単位の行）。 */
@Entity
@Table(name = "leave_balances")
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    @Column(name = "granted_days", nullable = false)
    private BigDecimal grantedDays;

    @Column(name = "used_days", nullable = false)
    private BigDecimal usedDays;

    @Column(name = "granted_on", nullable = false)
    private LocalDate grantedOn;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;


    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(Long leaveTypeId) { this.leaveTypeId = leaveTypeId; }
    public BigDecimal getGrantedDays() { return grantedDays; }
    public void setGrantedDays(BigDecimal grantedDays) { this.grantedDays = grantedDays; }
    public BigDecimal getUsedDays() { return usedDays; }
    public void setUsedDays(BigDecimal usedDays) { this.usedDays = usedDays; }
    public LocalDate getGrantedOn() { return grantedOn; }
    public void setGrantedOn(LocalDate grantedOn) { this.grantedOn = grantedOn; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
