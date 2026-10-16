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
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private final com.github.benmanes.caffeine.cache.Cache<String, java.util.concurrent.CopyOnWriteArrayList<Long>> registerRateLimit =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(1, java.util.concurrent.TimeUnit.MINUTES)
                    .build();

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserService userService,
            com.checador.service.BranchService branchService,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userService = userService;
        this.branchService = branchService;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    private boolean isRegisterRateLimited(String key, int maxAttempts) {
        long now = System.currentTimeMillis();
        java.util.concurrent.CopyOnWriteArrayList<Long> list = registerRateLimit.get(key, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        list.removeIf(t -> (now - t) > 60_000L);
        if (list.size() >= maxAttempts) {
            return true;
        }
        list.add(now);
        return false;
    }

    private String resolveClientIp(jakarta.servlet.http.HttpServletRequest req) {
        String addr = req.getRemoteAddr();
        return (addr != null && !addr.isBlank()) ? addr : "unknown";
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerEmployee(
            jakarta.servlet.http.HttpServletRequest servletRequest,
            @Valid @RequestBody RegisterRequest request) {
        String ip = resolveClientIp(servletRequest);
        String slug = request.slug() != null ? request.slug().trim().toLowerCase() : "";

        // Rate limit por IP (máx 5/min) y por Slug de empresa (máx 10/min)
        if (isRegisterRateLimited("ip:" + ip, 5) || (!slug.isEmpty() && isRegisterRateLimited("slug:" + slug, 10))) {
            return ResponseEntity.status(429).body(Map.of("error", "Demasiados intentos de registro. Espera 1 minuto e inténtalo de nuevo."));
        }

        if (slug.isBlank() || request.invitationCode() == null || request.invitationCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invitación o empresa inválida."));
        }

        // Buscar configuración de la empresa por slug
        java.util.List<com.checador.entity.TenantSettings> list = jdbcTemplate.query(
                "SELECT tenant_id, invitation_code_hash, invitation_expires_at FROM tenant_settings WHERE slug = ?",
                (rs, rowNum) -> {
                    com.checador.entity.TenantSettings ts = new com.checador.entity.TenantSettings();
                    ts.setTenantId(rs.getLong("tenant_id"));
                    ts.setInvitationCodeHash(rs.getString("invitation_code_hash"));
                    java.sql.Timestamp exp = rs.getTimestamp("invitation_expires_at");
                    if (exp != null) {
                        ts.setInvitationExpiresAt(exp.toLocalDateTime());
                    }
                    return ts;
                },
                slug
        );
        if (list.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invitación o empresa inválida."));
        }
        var tenantSettings = list.get(0);

        // Validar código de invitación contra hash y vencimiento
        String hash = tenantSettings.getInvitationCodeHash();
        if (hash == null || !passwordEncoder.matches(request.invitationCode().trim(), hash)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invitación o empresa inválida."));
        }
        if (tenantSettings.getInvitationExpiresAt() != null && java.time.LocalDateTime.now().isAfter(tenantSettings.getInvitationExpiresAt())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invitación o empresa inválida."));
        }

        Long tenantId = tenantSettings.getTenantId();
        com.checador.security.TenantContext.setTenantId(tenantId);
        try {
            // Validar que la sucursal pertenezca a ESA empresa
            com.checador.entity.Branch branch;
            try {
                branch = branchService.findByIdWithTenant(request.branchId());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invitación o empresa inválida."));
            }
            if (branch == null || !tenantId.equals(branch.getTenantId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invitación o empresa inválida."));
            }

            // Forzar rol EMPLOYEE e ignorar cualquier role, tenantId o active del cuerpo
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
            responseData.put("tenantId", tenantId);
            responseData.put("branchId", branch.getId());
            responseData.put("branchName", branch.getName());
            responseData.put("shiftType", user.getShiftType() != null ? user.getShiftType().name() : null);

            return ResponseEntity.ok(responseData);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Error al procesar el registro de empleado."));
        } finally {
            com.checador.security.TenantContext.clear();
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
            @jakarta.validation.constraints.NotNull(message = "El turno es requerido") com.checador.entity.ShiftType shiftType,
            @NotBlank(message = "La empresa es requerida") String slug,
            @NotBlank(message = "El código de invitación es requerido") String invitationCode,
            String role,
            Long tenantId,
            Boolean active
    ) {}
}

