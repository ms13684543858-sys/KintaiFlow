package com.example.kintaiflow.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** attendance_breaks テーブル（休憩・日中離席の1区間）。end_at が null の間は「休憩中／離席中」。 */
@Entity
@Table(name = "attendance_breaks")
public class AttendanceBreak {

    public static final String BREAK = "BREAK";   // 休憩（昼休みなど）
    public static final String AWAY = "AWAY";     // 日中離席（私用外出など）

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attendance_record_id", nullable = false)
    private Long attendanceRecordId;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected AttendanceBreak() {
    }

    public AttendanceBreak(Long attendanceRecordId, String kind, LocalDateTime startAt) {
        this.attendanceRecordId = attendanceRecordId;
        this.kind = kind;
        this.startAt = startAt;
    }

    /** 修正申請の反映用（開始・終了が確定している区間）。 */
    public AttendanceBreak(Long attendanceRecordId, String kind, LocalDateTime startAt, LocalDateTime endAt) {
        this(attendanceRecordId, kind, startAt);
        this.endAt = endAt;
    }

    public Long getId() { return id; }
    public Long getAttendanceRecordId() { return attendanceRecordId; }
    public String getKind() { return kind; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
}
