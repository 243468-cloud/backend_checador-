package com.checador.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tenant_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(name = "icon_url")
    private String iconUrl;

    @Column(name = "primary_color", length = 20)
    private String primaryColor = "#4F46E5";
    
    @Column(name = "tolerance_minutes")
    private Integer toleranceMinutes = 10;

    // --- Permisos y Módulos de la Empresa ---
    
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", length = 20)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.ACTIVE;

    @Column(name = "invitation_code_hash", length = 255)
    private String invitationCodeHash;

    @Column(name = "invitation_expires_at")
    private java.time.LocalDateTime invitationExpiresAt;

    @Transient
    private java.util.Set<String> activeModules = new java.util.HashSet<>();

    public enum SubscriptionStatus {
        ACTIVE, SUSPENDED, CANCELED, TRIAL
    }

    public static TenantSettingsBuilder builder() {
        return new TenantSettingsBuilder();
    }

    public static class TenantSettingsBuilder {
        private Long id;
        private String companyName;
        private String slug;
        private String logoUrl;
        private String iconUrl;
        private String primaryColor = "#4F46E5";
        private Integer toleranceMinutes = 10;
        private SubscriptionStatus subscriptionStatus = SubscriptionStatus.ACTIVE;
        private String invitationCodeHash;
        private java.time.LocalDateTime invitationExpiresAt;
        private java.util.Set<String> activeModules = new java.util.HashSet<>();

        public TenantSettingsBuilder id(Long id) { this.id = id; return this; }
        public TenantSettingsBuilder companyName(String companyName) { this.companyName = companyName; return this; }
        public TenantSettingsBuilder slug(String slug) { this.slug = slug; return this; }
        public TenantSettingsBuilder logoUrl(String logoUrl) { this.logoUrl = logoUrl; return this; }
        public TenantSettingsBuilder iconUrl(String iconUrl) { this.iconUrl = iconUrl; return this; }
        public TenantSettingsBuilder primaryColor(String primaryColor) { this.primaryColor = primaryColor; return this; }
        public TenantSettingsBuilder toleranceMinutes(Integer toleranceMinutes) { this.toleranceMinutes = toleranceMinutes; return this; }
        public TenantSettingsBuilder subscriptionStatus(SubscriptionStatus subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; return this; }
        public TenantSettingsBuilder invitationCodeHash(String invitationCodeHash) { this.invitationCodeHash = invitationCodeHash; return this; }
        public TenantSettingsBuilder invitationExpiresAt(java.time.LocalDateTime invitationExpiresAt) { this.invitationExpiresAt = invitationExpiresAt; return this; }
        public TenantSettingsBuilder activeModules(java.util.Set<String> activeModules) { this.activeModules = activeModules; return this; }

        public TenantSettings build() {
            TenantSettings ts = new TenantSettings();
            ts.setId(id);
            ts.setCompanyName(companyName);
            ts.setSlug(slug);
            ts.setLogoUrl(logoUrl);
            ts.setIconUrl(iconUrl);
            ts.setPrimaryColor(primaryColor);
            ts.setToleranceMinutes(toleranceMinutes);
            ts.setSubscriptionStatus(subscriptionStatus);
            ts.setInvitationCodeHash(invitationCodeHash);
            ts.setInvitationExpiresAt(invitationExpiresAt);
            ts.setActiveModules(activeModules);
            return ts;
        }
    }
}
