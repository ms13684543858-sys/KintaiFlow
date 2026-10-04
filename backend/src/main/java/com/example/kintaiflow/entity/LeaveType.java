package com.example.kintaiflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** leave_types テーブル（休暇種別）。 */
@Entity
@Table(name = "leave_types")
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "legal_basis")
    private String legalBasis;

    @Column(name = "is_paid", nullable = false)
    private Boolean isPaid;

    @Column(name = "max_days_rule")
    private String maxDaysRule;

    @Column(name = "allow_half_day", nullable = false)
    private Boolean allowHalfDay;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;


    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLegalBasis() { return legalBasis; }
    public void setLegalBasis(String legalBasis) { this.legalBasis = legalBasis; }
    public Boolean getIsPaid() { return isPaid; }
    public void setIsPaid(Boolean isPaid) { this.isPaid = isPaid; }
    public String getMaxDaysRule() { return maxDaysRule; }
    public void setMaxDaysRule(String maxDaysRule) { this.maxDaysRule = maxDaysRule; }
    public Boolean getAllowHalfDay() { return allowHalfDay; }
    public void setAllowHalfDay(Boolean allowHalfDay) { this.allowHalfDay = allowHalfDay; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
