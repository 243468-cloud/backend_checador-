package com.checador.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shift_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shift_name", nullable = false, unique = true, length = 50)
    private String shiftName; // MORNING, EVENING, SUNDAY, NOCTURNO, MEDIO

    @Column(name = "label", nullable = false, length = 100)
    private String label; // "Turno Matutino", "Turno Vespertino", "Turno Dominical", etc.

    @Column(name = "start_time", nullable = false, length = 10)
    private String startTime; // "07:00", "14:00", "08:00", "22:00"

    @Column(name = "end_time", nullable = false, length = 10)
    private String endTime; // "15:00", "22:00", "18:00", "06:00"

    @Column(name = "days_description", length = 150)
    private String daysDescription; // "Lunes a Sábado", "Solo Domingo", etc.
}
