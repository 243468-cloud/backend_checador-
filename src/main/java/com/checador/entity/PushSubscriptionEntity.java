package com.checador.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "push_subscriptions")
@Getter
@Setter
@Builder
public class PushSubscriptionEntity extends BaseEntity {

    public PushSubscriptionEntity() {
    }

    public PushSubscriptionEntity(Long id, User user, String endpoint, String p256dhKey, String authKey, String role, Long branchId) {
        this.id = id;
        this.user = user;
        this.endpoint = endpoint;
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
        this.role = role;
        this.branchId = branchId;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 500, nullable = false, unique = true)
    private String endpoint;

    @Column(columnDefinition = "TEXT")
    private String p256dhKey;

    @Column(columnDefinition = "TEXT")
    private String authKey;

    @Column(length = 20)
    private String role;

    private Long branchId;
}
