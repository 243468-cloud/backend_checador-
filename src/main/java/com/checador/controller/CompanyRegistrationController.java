package com.checador.controller;

import com.checador.entity.TenantSettings;
import com.checador.service.CompanyRegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class CompanyRegistrationController {

    private final CompanyRegistrationService registrationService;

    @PostMapping("/register-company")
    public ResponseEntity<?> registerCompany(@Valid @RequestBody CompanyRegistrationRequest req) {
        try {
            TenantSettings newCompany = registrationService.registerCompany(
                    req.companyName(),
                    req.slug(),
                    req.adminFullName(),
                    req.adminUsername(),
                    req.adminEmail(),
                    req.adminPassword()
            );

            return ResponseEntity.ok(Map.of(
                    "message", "Empresa registrada exitosamente",
                    "tenantId", newCompany.getTenantId(),
                    "companyName", newCompany.getCompanyName(),
                    "slug", newCompany.getSlug()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al registrar empresa: " + e.getMessage()));
        }
    }

    public record CompanyRegistrationRequest(
            @NotBlank(message = "El nombre de la empresa es requerido")
            @Size(min = 2, max = 150, message = "El nombre de la empresa debe tener entre 2 y 150 caracteres")
            String companyName,

            @NotBlank(message = "El identificador (slug) es requerido")
            @Pattern(regexp = "^[a-z0-9-]+$", message = "El identificador solo puede contener minúsculas, números y guiones")
            String slug,

            @NotBlank(message = "El nombre completo del administrador es requerido")
            String adminFullName,

            @NotBlank(message = "El nombre de usuario del administrador es requerido")
            @Size(min = 3, max = 50, message = "El usuario debe tener entre 3 y 50 caracteres")
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El usuario solo puede contener letras, números, puntos, guiones y guiones bajos")
            String adminUsername,

            @NotBlank(message = "El correo electrónico es requerido")
            @Email(message = "Formato de correo electrónico inválido")
            String adminEmail,

            @NotBlank(message = "La contraseña del administrador es requerida")
            @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
            String adminPassword
    ) {}
}
