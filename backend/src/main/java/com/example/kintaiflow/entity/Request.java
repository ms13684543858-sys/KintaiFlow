package com.example.kintaiflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** requests テーブル（休暇申請・打刻修正申請）。 */
@Entity
@Table(name = "requests")
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request_type", nullable = false)
    private String requestType;

    @Column(name = "leave_type_id")
    private Long leaveTypeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "unit")
    private String unit;

    @Column(name = "days")
    private BigDecimal days;

    @Column(name = "corrected_clock_in")
    private LocalDateTime correctedClockIn;

    @Column(name = "corrected_clock_out")
    private LocalDateTime correctedClockOut;

    @Column(name = "corrected_break_kind")
    private String correctedBreakKind;

    @Column(name = "corrected_break_start")
    private LocalDateTime correctedBreakStart;

    @Column(name = "corrected_break_end")
    private LocalDateTime correctedBreakEnd;

    @Column(name = "reason")
    private String reason;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "current_step", nullable = false)
    private Integer currentStep;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;


    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public Long getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(Long leaveTypeId) { this.leaveTypeId = leaveTypeId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getDays() { return days; }
    public void setDays(BigDecimal days) { this.days = days; }
    public LocalDateTime getCorrectedClockIn() { return correctedClockIn; }
    public void setCorrectedClockIn(LocalDateTime correctedClockIn) { this.correctedClockIn = correctedClockIn; }
    public LocalDateTime getCorrectedClockOut() { return correctedClockOut; }
    public void setCorrectedClockOut(LocalDateTime correctedClockOut) { this.correctedClockOut = correctedClockOut; }
    public String getCorrectedBreakKind() { return correctedBreakKind; }
    public void setCorrectedBreakKind(String correctedBreakKind) { this.correctedBreakKind = correctedBreakKind; }
    public LocalDateTime getCorrectedBreakStart() { return correctedBreakStart; }
    public void setCorrectedBreakStart(LocalDateTime correctedBreakStart) { this.correctedBreakStart = correctedBreakStart; }
    public LocalDateTime getCorrectedBreakEnd() { return correctedBreakEnd; }
    public void setCorrectedBreakEnd(LocalDateTime correctedBreakEnd) { this.correctedBreakEnd = correctedBreakEnd; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCurrentStep() { return currentStep; }
    public void setCurrentStep(Integer currentStep) { this.currentStep = currentStep; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
