package com.checador.controller;

import com.checador.entity.LeaveRequest;
import com.checador.entity.LeaveRequest.LeaveType;
import com.checador.entity.Role;
import com.checador.entity.User;
import com.checador.service.LeaveRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveRequestService leaveService;

    // ─────────────────────────────────────────────────────────────────────────
    // EMPLEADO — Ver mis solicitudes
    // GET /api/leaves/me
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<?> getMyRequests(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                leaveService.getMyRequests(user.getId())
                        .stream().map(this::toResponse).toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EMPLEADO — Crear solicitud
    // POST /api/leaves
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<?> createRequest(@AuthenticationPrincipal User user,
                                           @Valid @RequestBody CreateLeaveRequest req) {
        try {
            // Validar que la URL de evidencia (si existe) sea segura
            if (req.evidenceUrl() != null && !req.evidenceUrl().isBlank()) {
                String url = req.evidenceUrl().toLowerCase();
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "La URL de evidencia debe comenzar con http:// o https://"));
                }
            }
            // Validar que end_date >= start_date
            LocalDate start = LocalDate.parse(req.startDate());
            LocalDate end   = LocalDate.parse(req.endDate());
            if (end.isBefore(start)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "La fecha de fin no puede ser anterior a la fecha de inicio"));
            }
            LeaveType type = LeaveType.valueOf(req.requestType().toUpperCase());
            LeaveRequest created = leaveService.createRequest(
                    user, type, start, end, req.reason(), req.evidenceUrl());
            return ResponseEntity.ok(toResponse(created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Tipo de solicitud inválido: " + req.requestType()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN — Todas las solicitudes de la sucursal
    // GET /api/leaves/admin?branchId=X&pending=true
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/admin")
    public ResponseEntity<?> getBranchRequests(@AuthenticationPrincipal User admin,
                                               @RequestParam(required = false) Long branchId,
                                               @RequestParam(defaultValue = "false") boolean pending) {
        Long targetBranch = branchId != null ? branchId
                : (admin.getBranch() != null ? admin.getBranch().getId() : null);

        List<LeaveRequest> requests = pending
                ? leaveService.getPendingRequests(targetBranch)
                : leaveService.getBranchRequests(targetBranch);

        return ResponseEntity.ok(requests.stream().map(this::toResponse).toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN — Aprobar solicitud
    // PUT /api/leaves/{id}/approve
    // ─────────────────────────────────────────────────────────────────────────
    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id,
                                     @AuthenticationPrincipal User admin,
                                     @RequestBody(required = false) ReviewRequest req) {
        try {
            // Verificar que la solicitud pertenece a la sucursal del admin
            assertAdminCanReview(admin, id);
            String notes = req != null ? req.adminNotes() : null;
            LeaveRequest updated = leaveService.approve(id, admin.getId(), notes);
            return ResponseEntity.ok(toResponse(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN — Rechazar solicitud
    // PUT /api/leaves/{id}/reject
    // ─────────────────────────────────────────────────────────────────────────
    @PutMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id,
                                    @AuthenticationPrincipal User admin,
                                    @RequestBody ReviewRequest req) {
        try {
            if (req == null || req.adminNotes() == null || req.adminNotes().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Debes indicar el motivo del rechazo"));
            }
            assertAdminCanReview(admin, id);
            LeaveRequest updated = leaveService.reject(id, admin.getId(), req.adminNotes());
            return ResponseEntity.ok(toResponse(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Verifica que el admin tenga jurisdicción sobre la solicitud (misma sucursal).
     * SUPERUSER puede revisar solicitudes de cualquier sucursal.
     */
    private void assertAdminCanReview(User admin, Long requestId) {
        if (admin.getRole() == Role.SUPERUSER) return;
        LeaveRequest req = leaveService.findById(requestId);
        Long adminBranch   = admin.getBranch()          != null ? admin.getBranch().getId()          : null;
        Long requestBranch = req.getBranch()            != null ? req.getBranch().getId()            : null;
        if (!java.util.Objects.equals(adminBranch, requestBranch)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "No tienes permiso para revisar solicitudes de otra sucursal");
        }
    }

    private Map<String, Object> toResponse(LeaveRequest r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("employeeId", r.getUser().getId());
        m.put("employeeName", r.getUser().getFullName());
        m.put("requestType", r.getRequestType().name());
        m.put("startDate", r.getStartDate().toString());
        m.put("endDate", r.getEndDate().toString());
        m.put("reason", r.getReason());
        m.put("evidenceUrl", r.getEvidenceUrl());
        m.put("status", r.getStatus().name());
        m.put("adminNotes", r.getAdminNotes());
        m.put("reviewedBy", r.getReviewedBy());
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        return m;
    }

    public record CreateLeaveRequest(
            @NotBlank(message = "El tipo de solicitud es obligatorio")
            String requestType,

            @NotBlank(message = "La fecha de inicio es obligatoria")
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Formato de fecha inválido (use YYYY-MM-DD)")
            String startDate,

            @NotBlank(message = "La fecha de fin es obligatoria")
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Formato de fecha inválido (use YYYY-MM-DD)")
            String endDate,

            @NotBlank(message = "El motivo es obligatorio")
            @Size(min = 10, max = 1000, message = "El motivo debe tener entre 10 y 1000 caracteres")
            String reason,

            @Size(max = 500, message = "La URL de evidencia no puede exceder 500 caracteres")
            String evidenceUrl
    ) {}

    public record ReviewRequest(
            @Size(max = 500, message = "Las notas no pueden exceder 500 caracteres")
            String adminNotes
    ) {}
}
