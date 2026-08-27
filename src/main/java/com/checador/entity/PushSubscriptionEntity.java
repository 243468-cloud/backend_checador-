package com.checador.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "push_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscriptionEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT", nullable = false, unique = true)
    private String endpoint;

    @Column(columnDefinition = "TEXT")
    private String p256dhKey;

    @Column(columnDefinition = "TEXT")
    private String authKey;

    @Column(length = 20)
    private String role;

    private Long branchId;
}
