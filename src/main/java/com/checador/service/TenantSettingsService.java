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
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * Endpoint público para que el frontend obtenga el diseño antes de iniciar sesión.
     */
    public TenantSettings getSettingsBySlug(String slug) {
        return repository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Sucursal/Empresa no encontrada"));
    }

    /**
     * Endpoint privado para obtener la configuración del tenant actual (ya autenticado).
     */
    public TenantSettings getCurrentTenantSettings() {
        Long tenantId = TenantContext.getTenantId();
        return repository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Configuración no encontrada para el tenant actual"));
    }

    /**
     * Actualiza la configuración del tenant actual.
     */
    public TenantSettings updateCurrentSettings(TenantSettings updatedData) {
        TenantSettings current = getCurrentTenantSettings();
        
        if (updatedData.getCompanyName() != null) current.setCompanyName(updatedData.getCompanyName());
        if (updatedData.getLogoUrl() != null) current.setLogoUrl(updatedData.getLogoUrl());
        if (updatedData.getIconUrl() != null) current.setIconUrl(updatedData.getIconUrl());
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
            target.setActiveModules(modules);
            return repository.save(target);
        } finally {
            if (currentTenant != null) {
                TenantContext.setTenantId(currentTenant);
            } else {
                TenantContext.clear();
            }
        }
    }
}
