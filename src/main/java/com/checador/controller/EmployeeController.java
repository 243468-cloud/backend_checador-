package com.checador.controller;

import com.checador.entity.Role;
import com.checador.entity.ShiftType;
import com.checador.entity.User;
import com.checador.service.BranchService;
import com.checador.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final UserService userService;
    private final BranchService branchService;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getEmployees(@AuthenticationPrincipal User admin) {
        Long branchId = getBranchIdSafely(admin);
        List<User> employees = userService.getEmployeesByBranch(branchId);
        return ResponseEntity.ok(employees.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getById(@PathVariable Long id,
                                     @AuthenticationPrincipal User admin) {
        User target = userService.findById(id);
        assertSameBranchOrSuperuser(admin, target);
        return ResponseEntity.ok(toResponse(target));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@AuthenticationPrincipal User admin,
                                     @Valid @RequestBody EmployeeRequest req) {
        try {
            Long branchId = getBranchIdSafely(admin) != null ? getBranchIdSafely(admin) : req.branchId();
            if (branchId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Debe especificar una sucursal"));
            }
            var branch = branchService.findById(branchId);
            User employee = userService.createEmployee(req.username(), req.password(),
                    req.fullName(), req.email(), branch, req.shiftType());
            return ResponseEntity.ok(toResponse(employee));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long id,
                                     @AuthenticationPrincipal User admin,
                                     @RequestBody UpdateEmployeeRequest req) {
        User target = userService.findById(id);
        assertSameBranchOrSuperuser(admin, target);
        User updated = userService.updateUser(id, req.fullName(), req.email(),
                req.shiftType(), req.profilePicture());
        return ResponseEntity.ok(toResponse(updated));
    }

    @PatchMapping("/{id}/toggle")
    @Transactional
    public ResponseEntity<?> toggleActive(@PathVariable Long id,
                                          @AuthenticationPrincipal User admin) {
        User target = userService.findById(id);
        assertSameBranchOrSuperuser(admin, target);
        userService.toggleActive(id);
        return ResponseEntity.ok(Map.of("message", "Estado actualizado"));
    }

    @PatchMapping("/{id}/password")
    @Transactional
    public ResponseEntity<?> changePassword(@PathVariable Long id,
                                             @AuthenticationPrincipal User admin,
                                             @RequestBody Map<String, String> body) {
        User target = userService.findById(id);
        assertSameBranchOrSuperuser(admin, target);
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contraseña debe tener al menos 6 caracteres"));
        }
        userService.changePassword(id, newPassword);
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada"));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id,
                                     @AuthenticationPrincipal User admin) {
        User target = userService.findById(id);
        assertSameBranchOrSuperuser(admin, target);
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "Empleado eliminado exitosamente"));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Verifica que un ADMIN solo opere sobre empleados de su propia sucursal.
     * Los SUPERUSER pueden operar sobre cualquier sucursal.
     */
    private void assertSameBranchOrSuperuser(User admin, User target) {
        if (admin == null) return;
        if (admin.getRole() == Role.SUPERUSER) return; // Superuser: acceso total
        Long adminBranch  = admin.getBranch()  != null ? admin.getBranch().getId()  : null;
        Long targetBranch = target.getBranch() != null ? target.getBranch().getId() : null;
        if (!Objects.equals(adminBranch, targetBranch)) {
            throw new AccessDeniedException("No tienes permiso para operar sobre empleados de otra sucursal");
        }
    }

    private Long getBranchIdSafely(User u) {
        if (u == null) return null;
        try { return u.getBranch() != null ? u.getBranch().getId() : null; }
        catch (Exception e) { return null; }
    }

    private String getBranchNameSafely(User u) {
        if (u == null) return "";
        try { return u.getBranch() != null ? u.getBranch().getName() : ""; }
        catch (Exception e) { return ""; }
    }

    private Map<String, Object> toResponse(User u) {
        Map<String, Object> res = new java.util.HashMap<>();
        res.put("id", u.getId());
        res.put("username", u.getUsername());
        res.put("fullName", u.getFullName());
        res.put("email", u.getEmail() != null ? u.getEmail() : "");
        res.put("role", u.getRole().name());
        res.put("shiftType", u.getShiftType() != null ? u.getShiftType().name() : "");
        res.put("active", u.getActive());
        res.put("profilePicture", u.getProfilePicture() != null ? u.getProfilePicture() : "");
        res.put("branchId", getBranchIdSafely(u));
        res.put("branchName", getBranchNameSafely(u));
        return res;
    }

    public record EmployeeRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String fullName,
            String email,
            @NotNull ShiftType shiftType,
            Long branchId
    ) {}

    public record UpdateEmployeeRequest(String fullName, String email,
                                        ShiftType shiftType, String profilePicture) {}
}
