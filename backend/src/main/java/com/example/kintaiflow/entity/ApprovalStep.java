package com.example.kintaiflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** approval_steps テーブル（承認ステップ：1=上長、2=管理者）。 */
@Entity
@Table(name = "approval_steps")
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "comment")
    private String comment;

    @Column(name = "acted_at")
    private LocalDateTime actedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;


    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public Integer getStepNo() { return stepNo; }
    public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getActedAt() { return actedAt; }
    public void setActedAt(LocalDateTime actedAt) { this.actedAt = actedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
