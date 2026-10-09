package com.checador.controller;

import com.checador.entity.TenantSettings;
import com.checador.service.TenantSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class TenantSettingsController {

    private final TenantSettingsService service;

    /**
     * PÚBLICO: Obtiene colores y logo sin necesidad de Token JWT.
     */
    @GetMapping("/public/{slug}")
    public ResponseEntity<TenantSettings> getPublicSettings(@PathVariable String slug) {
        return ResponseEntity.ok(service.getSettingsBySlug(slug));
    }

    /**
     * PRIVADO: Obtiene la configuración del tenant que ha iniciado sesión.
     */
    @GetMapping("/current")
    public ResponseEntity<TenantSettings> getCurrentSettings() {
        return ResponseEntity.ok(service.getCurrentTenantSettings());
    }

    /**
     * PRIVADO (Solo SUPER_ADMIN/ADMIN): Actualiza la configuración.
     */
    @PutMapping("/current")
    public ResponseEntity<TenantSettings> updateCurrentSettings(@RequestBody TenantSettings settings) {
        return ResponseEntity.ok(service.updateCurrentSettings(settings));
    }

    /**
     * PRIVADO (Solo SUPERUSER): Obtiene todas las empresas registradas.
     */
    @GetMapping("/all")
    public ResponseEntity<java.util.List<TenantSettings>> getAllTenants() {
        return ResponseEntity.ok(service.getAllTenantsForSuperadmin());
    }
}
