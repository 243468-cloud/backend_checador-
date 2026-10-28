package com.checador.service;

import com.checador.entity.TenantSettings;
import com.checador.repository.TenantSettingsRepository;
import com.checador.security.TenantContext;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TenantSettingsService {

    private final TenantSettingsRepository repository;
    private final com.checador.repository.TenantModuleRepository moduleRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public TenantSettingsService(
            TenantSettingsRepository repository,
            com.checador.repository.TenantModuleRepository moduleRepository,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.moduleRepository = moduleRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    public String generateInvitationCode(Long tenantId, Integer daysValid) {
        TenantSettings settings = repository.findByTenantId(tenantId)
                .orElseThrow(() -> new com.checador.exception.ResourceNotFoundException("Configuración de empresa no encontrada"));

        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        String plainCode = sb.toString();

        settings.setInvitationCodeHash(passwordEncoder.encode(plainCode));
        if (daysValid != null && daysValid > 0) {
            settings.setInvitationExpiresAt(java.time.LocalDateTime.now().plusDays(daysValid));
        } else {
            settings.setInvitationExpiresAt(null);
        }
        repository.save(settings);
        return plainCode;
    }

    /**
     * Endpoint público para que el frontend obtenga el diseño antes de iniciar sesión.
     */
    public TenantSettings getSettingsBySlug(String slug) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT company_name, primary_color, logo_url, icon_url FROM tenant_settings WHERE slug = ?",
                (rs, rowNum) -> {
                    TenantSettings ts = new TenantSettings();
                    ts.setCompanyName(rs.getString("company_name"));
                    ts.setPrimaryColor(rs.getString("primary_color"));
                    ts.setLogoUrl(rs.getString("logo_url"));
                    ts.setIconUrl(rs.getString("icon_url"));
                    return ts;
                },
                slug
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Tenant not found");
        }
    }

    /**
     * Resuelve el tenant_id a partir de un slug de forma segura sin requerir contexto previo.
     */
    public Long getTenantIdBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM tenant_settings WHERE slug = ?",
                Long.class,
                slug
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * Endpoint privado para obtener la configuración del tenant actual (ya autenticado).
     */
    public TenantSettings getCurrentTenantSettings() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId == 0L) {
            java.util.Set<String> allModules = java.util.Set.of("schedules", "lateArrivals", "reports", "rankings");
            TenantSettings superSettings = repository.findByTenantId(0L).orElseGet(() -> {
                TenantSettings ts = new TenantSettings();
                ts.setTenantId(0L);
                ts.setCompanyName("Superadministrador");
                ts.setPrimaryColor("#4F46E5");
                ts.setSlug("superadmin");
                ts.setActiveModules(allModules);
                return ts;
            });
            if (superSettings.getActiveModules() == null || superSettings.getActiveModules().isEmpty()) {
                superSettings.setActiveModules(allModules);
            }
            return superSettings;
        }

        TenantSettings settings = repository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    TenantSettings fallback = new TenantSettings();
                    fallback.setTenantId(tenantId);
                    fallback.setCompanyName("Via Gourmet");
                    fallback.setSlug("tenant-" + tenantId);
                    fallback.setPrimaryColor("#4F46E5");
                    fallback.setToleranceMinutes(10);
                    return repository.save(fallback);
                });
        java.util.List<com.checador.entity.TenantModule> mods = moduleRepository.findByTenantId(tenantId);
        settings.setActiveModules(mods.stream().map(com.checador.entity.TenantModule::getModuleName).collect(java.util.stream.Collectors.toSet()));
        return settings;
    }

    /**
     * Actualiza la configuración del tenant actual.
     */
    public TenantSettings updateCurrentSettings(TenantSettings updatedData) {
        String logoUrl = updatedData.getLogoUrl() != null
                ? ImageUrlValidator.validate(updatedData.getLogoUrl(), "logoUrl") : null;
        String iconUrl = updatedData.getIconUrl() != null
                ? ImageUrlValidator.validate(updatedData.getIconUrl(), "iconUrl") : null;

        TenantSettings current = getCurrentTenantSettings();
        
        if (updatedData.getCompanyName() != null) current.setCompanyName(updatedData.getCompanyName());
        if (logoUrl != null) current.setLogoUrl(logoUrl);
        if (iconUrl != null) current.setIconUrl(iconUrl);
        if (updatedData.getPrimaryColor() != null) current.setPrimaryColor(updatedData.getPrimaryColor());
        if (updatedData.getToleranceMinutes() != null) current.setToleranceMinutes(updatedData.getToleranceMinutes());
        
        return repository.save(current);
    }

    /**
     * PRIVADO (Solo SUPERUSER): Obtiene todas las empresas registradas.
     */
    public java.util.List<TenantSettings> getAllTenantsForSuperadmin() {
        return jdbcTemplate.query("SELECT * FROM tenant_settings", (rs, rowNum) -> {
            TenantSettings ts = new TenantSettings();
            ts.setId(rs.getLong("id"));
            Long tId = rs.getLong("tenant_id");
            ts.setTenantId(tId);
            ts.setCompanyName(rs.getString("company_name"));
            ts.setSlug(rs.getString("slug"));
            ts.setLogoUrl(rs.getString("logo_url"));
            ts.setIconUrl(rs.getString("icon_url"));
            ts.setPrimaryColor(rs.getString("primary_color"));
            ts.setToleranceMinutes(rs.getInt("tolerance_minutes"));
            String subStatus = rs.getString("subscription_status");
            if (subStatus != null) {
                try {
                    ts.setSubscriptionStatus(TenantSettings.SubscriptionStatus.valueOf(subStatus));
                } catch (Exception ignored) {}
            }
            java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
            if (createdTs != null) {
                ts.setCreatedAt(createdTs.toLocalDateTime());
            }
            java.util.List<String> mods = jdbcTemplate.queryForList(
                    "SELECT module_name FROM tenant_modules WHERE tenant_id = ?", String.class, tId);
            ts.setActiveModules(new java.util.HashSet<>(mods));
            return ts;
        });
    }

    /**
     * PRIVADO (Solo SUPERUSER): Actualiza el branding (nombre, logo, ícono, color, estado) de una empresa.
     */
    public TenantSettings updateBrandingForTenant(Long targetTenantId, TenantSettings updatedData) {
        String logoUrl = (updatedData.getLogoUrl() != null && !updatedData.getLogoUrl().isBlank())
                ? ImageUrlValidator.validate(updatedData.getLogoUrl(), "logoUrl") : null;
        String iconUrl = (updatedData.getIconUrl() != null && !updatedData.getIconUrl().isBlank())
                ? ImageUrlValidator.validate(updatedData.getIconUrl(), "iconUrl") : null;

        jdbcTemplate.update(
                "UPDATE tenant_settings SET " +
                "company_name = COALESCE(?, company_name), " +
                "primary_color = COALESCE(?, primary_color), " +
                "logo_url = ?, " +
                "icon_url = ?, " +
                "subscription_status = COALESCE(?, subscription_status) " +
                "WHERE tenant_id = ?",
                updatedData.getCompanyName(),
                updatedData.getPrimaryColor(),
                logoUrl,
                iconUrl,
                updatedData.getSubscriptionStatus() != null ? updatedData.getSubscriptionStatus().name() : null,
                targetTenantId
        );

        return getAllTenantsForSuperadmin().stream()
                .filter(t -> targetTenantId.equals(t.getTenantId()))
                .findFirst()
                .orElseThrow(() -> new com.checador.exception.ResourceNotFoundException("Empresa no encontrada"));
    }

    /**
     * PRIVADO (Solo SUPERUSER): Actualiza los módulos activos de una empresa.
     */
    public TenantSettings updateModulesForTenant(Long targetTenantId, java.util.Set<String> modules) {
        jdbcTemplate.update("DELETE FROM tenant_modules WHERE tenant_id = ?", targetTenantId);
        if (modules != null && !modules.isEmpty()) {
            for (String moduleName : modules) {
                jdbcTemplate.update(
                        "INSERT INTO tenant_modules (tenant_id, module_name) VALUES (?, ?)",
                        targetTenantId, moduleName);
            }
        }

        List<TenantSettings> list = jdbcTemplate.query(
                "SELECT * FROM tenant_settings WHERE tenant_id = ? ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> {
                    TenantSettings ts = new TenantSettings();
                    ts.setId(rs.getLong("id"));
                    ts.setTenantId(rs.getLong("tenant_id"));
                    ts.setCompanyName(rs.getString("company_name"));
                    ts.setSlug(rs.getString("slug"));
                    ts.setLogoUrl(rs.getString("logo_url"));
                    ts.setIconUrl(rs.getString("icon_url"));
                    ts.setPrimaryColor(rs.getString("primary_color"));
                    ts.setToleranceMinutes(rs.getInt("tolerance_minutes"));
                    return ts;
                },
                targetTenantId
        );
        if (list.isEmpty()) {
            throw new RuntimeException("Empresa no encontrada");
        }
        TenantSettings target = list.get(0);
        target.setActiveModules(modules != null ? modules : java.util.Collections.emptySet());
        return target;
    }
}
