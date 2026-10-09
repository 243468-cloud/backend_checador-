package com.checador.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tenant_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantSettings extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    // Identificador público para la URL (ej. "mi-empresa")
    @Column(name = "slug", nullable = false, unique = true, length = 50)
    private String slug;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "primary_color", length = 20)
    private String primaryColor;
    
    @Column(name = "tolerance_minutes")
    private Integer toleranceMinutes = 10;

    // --- Permisos y Módulos de la Empresa ---
    
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", length = 20)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.ACTIVE;

    @Column(name = "is_gps_enabled")
    private Boolean isGpsEnabled = true;

    @Column(name = "is_payroll_enabled")
    private Boolean isPayrollEnabled = false;

    public enum SubscriptionStatus {
        ACTIVE, SUSPENDED, CANCELED, TRIAL
    }
}
