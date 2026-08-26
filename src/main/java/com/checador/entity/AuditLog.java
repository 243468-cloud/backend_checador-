package com.checador.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_action_date", columnList = "action, created_at"),
        @Index(name = "idx_audit_user_date",   columnList = "user_id, created_at"),
        @Index(name = "idx_audit_entity",      columnList = "entity_type, entity_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User performedBy;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;
}
