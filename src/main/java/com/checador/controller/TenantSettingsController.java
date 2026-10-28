package com.checador.controller;

import com.checador.entity.TenantSettings;
import com.checador.service.TenantSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class TenantSettingsController {

    private final TenantSettingsService service;

    public TenantSettingsController(TenantSettingsService service) {
        this.service = service;
    }

    /**
     * PÚBLICO: Obtiene colores y logo sin necesidad de Token JWT.
     */
    @GetMapping("/public/{slug}")
    public ResponseEntity<java.util.Map<String, String>> getPublicSettings(@PathVariable String slug) {
        TenantSettings settings = service.getSettingsBySlug(slug);
        java.util.Map<String, String> result = new java.util.HashMap<>();
        result.put("companyName", settings.getCompanyName());
        result.put("primaryColor", settings.getPrimaryColor());
        result.put("logoUrl", settings.getLogoUrl());
        result.put("iconUrl", settings.getIconUrl());
        return ResponseEntity.ok(result);
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
     * PRIVADO (Solo ADMIN): Genera o rota el código de invitación para empleados.
     */
    @PostMapping("/current/invitation-code")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> generateInvitationCode(
            @RequestParam(required = false) Integer daysValid,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        Integer effectiveDays = daysValid;
        if (effectiveDays == null && body != null && body.get("daysValid") instanceof Number) {
            effectiveDays = ((Number) body.get("daysValid")).intValue();
        }
        if (effectiveDays == null || effectiveDays <= 0) {
            effectiveDays = 7;
        }
        Long tenantId = com.checador.security.TenantContext.getTenantId();
        String code = service.generateInvitationCode(tenantId, effectiveDays);
        String expiresAt = java.time.LocalDateTime.now().plusDays(effectiveDays).toString();
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("invitationCode", code);
        response.put("code", code);
        response.put("expiresAt", expiresAt);
        response.put("message", "Código de invitación generado exitosamente.");
        return ResponseEntity.ok(response);
    }

    /**
     * PRIVADO (Solo SUPERUSER): Obtiene todas las empresas registradas.
     */
    @GetMapping("/all")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPERUSER')")
    public ResponseEntity<java.util.List<TenantSettings>> getAllTenants() {
        return ResponseEntity.ok(service.getAllTenantsForSuperadmin());
    }

    /**
     * PRIVADO (Solo SUPERUSER): Actualiza los módulos de una empresa.
     */
    @PutMapping("/tenant/{tenantId}/modules")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPERUSER')")
    public ResponseEntity<TenantSettings> updateModules(
            @PathVariable Long tenantId,
            @RequestBody java.util.Set<String> modules) {
        return ResponseEntity.ok(service.updateModulesForTenant(tenantId, modules));
    }
}
