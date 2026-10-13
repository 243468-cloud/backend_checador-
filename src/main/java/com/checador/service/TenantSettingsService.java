package com.checador.service;

import com.checador.entity.TenantSettings;
import com.checador.repository.TenantSettingsRepository;
import com.checador.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantSettingsService {

    private final TenantSettingsRepository repository;
    private final com.checador.repository.TenantModuleRepository moduleRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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
     * Endpoint privado para obtener la configuración del tenant actual (ya autenticado).
     */
    public TenantSettings getCurrentTenantSettings() {
        Long tenantId = TenantContext.getTenantId();
        TenantSettings settings = repository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Configuración no encontrada para el tenant actual"));
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
            ts.setTenantId(rs.getLong("tenant_id"));
            ts.setCompanyName(rs.getString("company_name"));
            ts.setSlug(rs.getString("slug"));
            ts.setLogoUrl(rs.getString("logo_url"));
            ts.setIconUrl(rs.getString("icon_url"));
            ts.setPrimaryColor(rs.getString("primary_color"));
            ts.setToleranceMinutes(rs.getInt("tolerance_minutes"));
            
            // Note: active_modules is a separate table, fetching it per row might be N+1,
            // but since this is superadmin and small scale, we can query it or leave it null/empty.
            return ts;
        });
    }

    /**
     * PRIVADO (Solo SUPERUSER): Actualiza los módulos activos de una empresa.
     */
    public TenantSettings updateModulesForTenant(Long targetTenantId, java.util.Set<String> modules) {
        Long currentTenant = TenantContext.getTenantId();
        try {
            // Para editar el tenant destino, nos "cambiamos" temporalmente a ese tenant
            TenantContext.setTenantId(targetTenantId);
            TenantSettings target = repository.findByTenantId(targetTenantId)
                    .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));
            
            // Delete old and insert new
            moduleRepository.deleteByTenantId(targetTenantId);
            if (modules != null && !modules.isEmpty()) {
                java.util.List<com.checador.entity.TenantModule> newMods = modules.stream()
                    .map(m -> new com.checador.entity.TenantModule(targetTenantId, m))
                    .toList();
                moduleRepository.saveAll(newMods);
            }
            target.setActiveModules(modules);
            return target;
        } finally {
            if (currentTenant != null) {
                TenantContext.setTenantId(currentTenant);
            } else {
                TenantContext.clear();
            }
        }
    }
}
