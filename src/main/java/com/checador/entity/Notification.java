package com.checador.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Notificación in-app generada por eventos del sistema (check-in, check-out, etc.)
 * Dirigida principalmente al SUPERUSER/ADMIN de la sucursal correspondiente.
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notif_recipient", columnList = "recipient_role, read_at, created_at"),
        @Index(name = "idx_notif_branch",    columnList = "branch_id, created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    public enum NotifType {
        CHECK_IN, CHECK_OUT, LEAVE_REQUEST, LEAVE_APPROVED, LEAVE_REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Rol al que va dirigida la notificación (SUPERUSER, ADMIN) */
    @Column(name = "recipient_role", nullable = false, length = 20)
    private String recipientRole;

    /** Sucursal del evento (null = aplica a todos / SUPERUSER global) */
    @Column(name = "branch_id")
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotifType type;

    /** Título breve (ej: "✅ Juan Pérez — Entrada registrada") */
    @Column(nullable = false, length = 200)
    private String title;

    /** Detalle completo del evento */
    @Column(nullable = false, length = 1000)
    private String body;

    /** Emoji/icono del evento */
    @Column(length = 10)
    private String icon;

    /** Cuándo fue leída (null = no leída) */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    public boolean isRead() {
        return readAt != null;
    }
}
