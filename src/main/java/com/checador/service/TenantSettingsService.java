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
        if (updatedData.getPrimaryColor() != null) current.setPrimaryColor(updatedData.getPrimaryColor());
        if (updatedData.getToleranceMinutes() != null) current.setToleranceMinutes(updatedData.getToleranceMinutes());
        
        return repository.save(current);
    }
}
