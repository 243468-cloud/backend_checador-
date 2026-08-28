package com.checador.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
    name = "attendances",
    indexes = {
        @Index(name = "idx_att_user_date",   columnList = "user_id, attendance_date"),
        @Index(name = "idx_att_branch_date", columnList = "branch_id, attendance_date"),
        @Index(name = "idx_att_status",      columnList = "status"),
        @Index(name = "idx_att_shift_date",  columnList = "shift_type, attendance_date")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_att_user_day", columnNames = {"user_id", "attendance_date"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", nullable = false, length = 50)
    private ShiftType shiftType;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    // Ubicación de check-in (DECIMAL(10,7) = precisión GPS de ~1 cm)
    @Column(name = "check_in_latitude", columnDefinition = "DECIMAL(10,7)")
    private Double checkInLatitude;

    @Column(name = "check_in_longitude", columnDefinition = "DECIMAL(10,7)")
    private Double checkInLongitude;

    // Ubicación de check-out
    @Column(name = "check_out_latitude", columnDefinition = "DECIMAL(10,7)")
    private Double checkOutLatitude;

    @Column(name = "check_out_longitude", columnDefinition = "DECIMAL(10,7)")
    private Double checkOutLongitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status = AttendanceStatus.IN_SHIFT;

    @Column(name = "late_minutes")
    private Integer lateMinutes = 0;

    @Column(name = "hours_worked", columnDefinition = "DECIMAL(5,2)")
    private Double hoursWorked;

    @Column(name = "extra_hours", columnDefinition = "DECIMAL(5,2)")
    private Double extraHours;

    @Column(name = "notes", length = 500)
    private String notes;

    // Retorna las horas reales trabajadas calculadas directamente desde las marcas de entrada y salida (máximo 14h por turno)
    public double getActualHoursWorked() {
        if (checkInTime != null && checkOutTime != null) {
            long minutes = java.time.Duration.between(checkInTime, checkOutTime).toMinutes();
            double hours = Math.max(0.0, Math.round((minutes / 60.0) * 10.0) / 10.0);
            return Math.min(hours, 14.0);
        }
        return hoursWorked != null ? Math.min(hoursWorked, 14.0) : 0.0;
    }

    // Para calcular horas trabajadas al hacer check-out
    public void calculateHoursWorked() {
        this.hoursWorked = getActualHoursWorked();
    }

    // Retorna las horas extra efectivas (manuales asignadas por admin o calculadas automáticamente por check-out en ubicación)
    public double getEffectiveExtraHours(double shiftHours) {
        if (extraHours != null && extraHours >= 0) {
            return extraHours;
        }
        double h = getActualHoursWorked();
        double rawExtra = Math.max(0.0, h - shiftHours);
        return Math.min(rawExtra, 6.0);
    }
}
