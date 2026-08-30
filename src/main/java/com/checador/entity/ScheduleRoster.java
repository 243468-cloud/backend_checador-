package com.checador.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
    name = "schedule_rosters",
    indexes = {
        @Index(name = "idx_roster_branch_week", columnList = "branch_id, week_start"),
        @Index(name = "idx_roster_area",        columnList = "area_name"),
        @Index(name = "idx_roster_day",         columnList = "day_index")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleRoster extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /** Clave de fila interna, ej. 'r-1' */
    @Column(name = "row_key", nullable = false, length = 50)
    private String rowKey;

    /** Nombre del área, ej. 'COCINA', 'BARRA' */
    @Column(name = "area_name", nullable = false, length = 100)
    private String areaName;

    /** Horario del turno, ej. '7AM-3PM' */
    @Column(name = "shift_time", length = 100)
    private String shiftTime;

    /** 0 = Lunes … 6 = Domingo */
    @Column(name = "day_index", nullable = false)
    private Integer dayIndex;

    /** Nombre del empleado asignado o 'DESCANSO' */
    @Column(name = "employee_name", nullable = false, length = 150)
    private String employeeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_type", nullable = false, length = 20)
    private RosterStatus statusType = RosterStatus.NORMAL;

    /** Lunes de la semana a la que pertenece este roster */
    @Column(name = "week_start")
    private LocalDate weekStart;

    /** Horario de inicio de turno cambiado, ej. '14:00' */
    @Column(name = "shift_start_time", length = 20)
    private String shiftStartTime;

    /** Horario de fin de turno cambiado, ej. '22:00' */
    @Column(name = "shift_end_time", length = 20)
    private String shiftEndTime;

    /** Horario de inicio del segundo turno (Doble) */
    @Column(name = "second_shift_start_time", length = 20)
    private String secondShiftStartTime;

    /** Horario de fin del segundo turno (Doble) */
    @Column(name = "second_shift_end_time", length = 20)
    private String secondShiftEndTime;

    /** Área destino para cambio de área, ej. 'BARRA' */
    @Column(name = "target_area", length = 100)
    private String targetArea;

    /** Motivo o justificación del cambio de turno/área */
    @Column(name = "reason", length = 255)
    private String reason;

    /** Usuario o rol que registró el cambio para auditoría */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    public enum RosterStatus {
        NORMAL, DESCANSO, CAMBIO_TURNO, DOBLE_TURNO, CAMBIO_AREA
    }
}
