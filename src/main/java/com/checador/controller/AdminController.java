package com.checador.controller;

import com.checador.entity.Branch;
import com.checador.entity.Role;
import com.checador.entity.User;
import com.checador.service.BranchService;
import com.checador.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admins")
public class AdminController {

    private final UserService userService;
    private final BranchService branchService;
    private final com.checador.repository.UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public AdminController(UserService userService, BranchService branchService,
                           com.checador.repository.UserRepository userRepository,
                           org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.branchService = branchService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAdmins() {
        List<User> admins = userService.getAdmins();
        return ResponseEntity.ok(admins.stream().map(this::toResponse).toList());
    }

    @GetMapping("/superusers")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getSuperusers() {
        List<User> superusers = userRepository.findByRole(Role.SUPERUSER);
        return ResponseEntity.ok(superusers.stream().map(this::toResponse).toList());
    }

    @PostMapping("/superusers")
    @Transactional
    public ResponseEntity<?> createSuperuser(@Valid @RequestBody SuperuserRequest req) {
        String cleanUsername = req.username() != null ? req.username().trim() : "";
        String cleanPassword = req.password() != null ? req.password().trim() : "";
        if (cleanUsername.isBlank() || cleanPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Usuario y contraseña requeridos"));
        }
        if (userRepository.existsByUsername(cleanUsername)) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre de usuario ya existe"));
        }
        User superuser = User.builder()
                .username(cleanUsername)
                .password(passwordEncoder.encode(cleanPassword))
                .fullName(req.fullName() != null ? req.fullName().trim() : cleanUsername)
                .email(req.email() != null ? req.email().trim() : null)
                .role(Role.SUPERUSER)
                .active(true)
                .build();
        superuser.setTenantId(0L);
        return ResponseEntity.ok(toResponse(userRepository.save(superuser)));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createAdmin(@Valid @RequestBody AdminRequest req) {
        try {
            Branch branch = branchService.findByIdWithTenant(req.branchId());
            User admin = userService.createAdmin(req.username(), req.password(),
                    req.fullName(), req.email(), branch);
            return ResponseEntity.ok(toResponse(admin));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateAdminRequest req) {
        User admin = userService.updateUser(id, req.fullName(), req.email(), null, null);
        return ResponseEntity.ok(toResponse(admin));
    }

    @PatchMapping("/{id}/toggle")
    @Transactional
    public ResponseEntity<?> toggleActive(@PathVariable Long id) {
        userService.toggleActive(id);
        return ResponseEntity.ok(Map.of("message", "Estado actualizado"));
    }

    @PatchMapping("/{id}/password")
    @Transactional
    public ResponseEntity<?> changePassword(@PathVariable Long id,
                                             @RequestBody java.util.Map<String, String> body) {
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "La contraseña debe tener al menos 6 caracteres"));
        }
        userService.changePassword(id, newPassword);
        return ResponseEntity.ok(java.util.Map.of("message", "Contraseña de administrador actualizada"));
    }

    private Long getBranchIdSafely(User u) {
        if (u == null) return null;
        try {
            return u.getBranch() != null ? u.getBranch().getId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getBranchNameSafely(User u) {
        if (u == null) return "";
        try {
            return u.getBranch() != null ? u.getBranch().getName() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private Map<String, Object> toResponse(User u) {
        Map<String, Object> res = new java.util.HashMap<>();
        res.put("id", u.getId());
        res.put("username", u.getUsername());
        res.put("fullName", u.getFullName());
        res.put("email", u.getEmail() != null ? u.getEmail() : "");
        res.put("role", u.getRole().name());
        res.put("active", u.getActive());
        res.put("branchId", getBranchIdSafely(u));
        res.put("branchName", getBranchNameSafely(u));
        return res;
    }

    public record SuperuserRequest(@NotBlank String username, @NotBlank String password, @NotBlank String fullName, String email) {}

    public record AdminRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String fullName,
            String email,
            @NotNull Long branchId
    ) {}

    public record UpdateAdminRequest(String fullName, String email) {}
}

