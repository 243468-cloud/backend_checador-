package com.checador.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
    name = "leave_requests",
    indexes = {
        @Index(name = "idx_leave_user",   columnList = "user_id, status"),
        @Index(name = "idx_leave_branch", columnList = "branch_id, status"),
        @Index(name = "idx_leave_dates",  columnList = "start_date, end_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private LeaveType requestType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    /** URL o ruta al archivo de evidencia (imagen, PDF) */
    @Column(name = "evidence_url", length = 255)
    private String evidenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(name = "admin_notes", length = 500)
    private String adminNotes;

    /** ID del administrador que revisó la solicitud */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    // ─── Enums ────────────────────────────────────────────────────────────────

    public enum LeaveType {
        PERMISO, INCAPACIDAD, VACACIONES, JUSTIFICANTE
    }

    public enum LeaveStatus {
        PENDING, APPROVED, REJECTED
    }
}
