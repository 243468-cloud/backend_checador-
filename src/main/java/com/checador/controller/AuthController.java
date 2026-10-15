package com.checador.controller;

import com.checador.entity.Role;
import com.checador.entity.User;
import com.checador.security.JwtService;
import com.checador.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;
    private final com.checador.service.BranchService branchService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserService userService,
            com.checador.service.BranchService branchService,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userService = userService;
        this.branchService = branchService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerEmployee(@Valid @RequestBody RegisterRequest request) {
        try {
            var branch = branchService.findByIdWithTenant(request.branchId());
            User user = userService.createEmployee(
                    request.username(),
                    request.password(),
                    request.fullName(),
                    request.email(),
                    branch,
                    request.shiftType()
            );

            String token = jwtService.generateToken(user);
            String refresh = jwtService.generateRefreshToken(user);

            Map<String, Object> responseData = new java.util.HashMap<>();
            responseData.put("token", token);
            responseData.put("refreshToken", refresh);
            responseData.put("role", user.getRole().name());
            responseData.put("fullName", user.getFullName());
            responseData.put("userId", user.getId());
            responseData.put("branchId", user.getBranch() != null ? user.getBranch().getId() : null);
            responseData.put("branchName", user.getBranch() != null ? user.getBranch().getName() : null);
            responseData.put("shiftType", user.getShiftType() != null ? user.getShiftType().name() : null);

            return ResponseEntity.ok(responseData);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Error al registrar empleado: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String username = request.username() != null ? request.username().trim() : "";
        String password = request.password() != null ? request.password().trim() : "";
        String slug = request.slug() != null ? request.slug().trim() : null;

        Long tenantId = null;
        String tenantSlug = null;

        if (slug != null && !slug.isBlank()) {
            try {
                tenantId = jdbcTemplate.queryForObject(
                        "SELECT tenant_id FROM tenant_settings WHERE slug = ?", Long.class, slug);
                tenantSlug = slug;
            } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                return ResponseEntity.status(401).body(Map.of("error", "Empresa no encontrada o credenciales inválidas."));
            }
        } else {
            java.util.List<Long> matchedTenants = jdbcTemplate.query(
                    "SELECT tenant_id FROM users WHERE LOWER(username) = LOWER(?)",
                    (rs, rowNum) -> rs.getLong("tenant_id"),
                    username
            );
            if (matchedTenants.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("error", "Credenciales incorrectas. Verifica tu usuario o contraseña."));
            }
            if (matchedTenants.size() > 1) {
                return ResponseEntity.status(401).body(Map.of("error", "Credenciales incorrectas. Verifica tu usuario o contraseña."));
            }
            tenantId = matchedTenants.get(0);
            try {
                tenantSlug = jdbcTemplate.queryForObject(
                        "SELECT slug FROM tenant_settings WHERE tenant_id = ?", String.class, tenantId);
            } catch (Exception ignored) {}
        }

        com.checador.security.TenantContext.setTenantId(tenantId);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));

            User user = (User) userService.loadUserByUsername(username);
            String token = jwtService.generateToken(user);
            String refresh = jwtService.generateRefreshToken(user);

            Map<String, Object> responseData = new java.util.HashMap<>();
            responseData.put("token", token);
            responseData.put("refreshToken", refresh);
            responseData.put("role", user.getRole().name());
            responseData.put("fullName", user.getFullName());
            responseData.put("userId", user.getId());
            responseData.put("tenantId", user.getTenantId());
            responseData.put("tenantSlug", tenantSlug);
            responseData.put("branchId", user.getBranch() != null ? user.getBranch().getId() : null);
            responseData.put("branchName", user.getBranch() != null ? user.getBranch().getName() : null);
            responseData.put("shiftType", user.getShiftType() != null ? user.getShiftType().name() : null);

            return ResponseEntity.ok(responseData);
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Credenciales incorrectas. Verifica tu usuario o contraseña."));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Credenciales incorrectas. Verifica tu usuario o contraseña."));
        } finally {
            com.checador.security.TenantContext.clear();
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Refresh token es requerido"));
        }
        try {
            String username = jwtService.extractUsername(refreshToken);
            Long tenantId = jwtService.extractTenantId(refreshToken);
            if (tenantId == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Refresh token inválido"));
            }
            com.checador.security.TenantContext.setTenantId(tenantId);
            try {
                User user = (User) userService.loadUserByUsername(username);
                if (jwtService.isRefreshTokenValid(refreshToken, user)) {
                    String newToken = jwtService.generateToken(user);
                    return ResponseEntity.ok(Map.of("token", newToken));
                } else {
                    return ResponseEntity.status(401).body(Map.of("error", "Refresh token expirado o inválido"));
                }
            } finally {
                com.checador.security.TenantContext.clear();
            }
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Refresh token inválido"));
        }
    }

    /**
     * P7 — Valida la sesión activa y devuelve el perfil del usuario autenticado.
     * El frontend lo llama al restaurar sesión para evitar confiar ciegamente en localStorage.
     * Spring Security ya validó el JWT antes de llegar aquí (vía JwtAuthFilter).
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }
        Map<String, Object> res = new java.util.HashMap<>();
        res.put("role", user.getRole().name());
        res.put("fullName", user.getFullName());
        res.put("userId", user.getId());
        res.put("tenantId", user.getTenantId());
        res.put("branchId", user.getBranch() != null ? user.getBranch().getId() : null);
        res.put("branchName", user.getBranch() != null ? user.getBranch().getName() : null);
        res.put("shiftType", user.getShiftType() != null ? user.getShiftType().name() : null);
        return ResponseEntity.ok(res);
    }


    public record LoginRequest(
            @NotBlank(message = "El nombre de usuario es requerido") String username,
            @NotBlank(message = "La contraseña es requerida") String password,
            String slug
    ) {}

    public record RegisterRequest(
            @NotBlank(message = "El nombre de usuario es requerido")
            @jakarta.validation.constraints.Size(min = 3, max = 50, message = "El usuario debe tener entre 3 y 50 caracteres")
            @jakarta.validation.constraints.Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El usuario solo puede contener letras, números, puntos, guiones y guiones bajos")
            String username,

            @NotBlank(message = "La contraseña es requerida")
            @jakarta.validation.constraints.Size(min = 6, max = 100, message = "La contraseña debe tener al menos 6 caracteres")
            String password,

            @NotBlank(message = "El nombre completo es requerido")
            @jakarta.validation.constraints.Size(min = 2, max = 100, message = "El nombre completo debe tener entre 2 y 100 caracteres")
            String fullName,

            @jakarta.validation.constraints.Email(message = "Formato de correo electrónico inválido")
            String email,

            @jakarta.validation.constraints.NotNull(message = "La sucursal es requerida") Long branchId,
            @jakarta.validation.constraints.NotNull(message = "El turno es requerido") com.checador.entity.ShiftType shiftType
    ) {}
}

