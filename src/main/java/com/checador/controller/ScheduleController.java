package com.checador.controller;

import com.checador.entity.Role;
import com.checador.entity.ScheduleRoster;
import com.checador.entity.User;
import com.checador.security.TenantContext;
import com.checador.service.ScheduleService;
import com.checador.service.TenantSettingsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedules")
@com.checador.security.RequiresModule("schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final com.checador.repository.BranchRepository branchRepository;
    private final TenantSettingsService tenantSettingsService;

    public ScheduleController(
            ScheduleService scheduleService,
            com.checador.repository.BranchRepository branchRepository,
            TenantSettingsService tenantSettingsService) {
        this.scheduleService = scheduleService;
        this.branchRepository = branchRepository;
        this.tenantSettingsService = tenantSettingsService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/schedules?branchId=X&weekStart=YYYY-MM-DD
    // Carga la matriz semanal de una sucursal.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<?> getRoster(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {

        boolean isSuperuser = isSuperuserContext(user);
        Long targetBranch = resolveBranch(user, branchId);
        try {
            if (isSuperuser) {
                switchTenantToBranchOwner(targetBranch);
            }
            List<ScheduleRoster> roster = scheduleService.getRoster(targetBranch, weekStart);
            return ResponseEntity.ok(roster.stream().map(this::toResponse).toList());
        } finally {
            if (isSuperuser) {
                TenantContext.setTenantId(0L);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/schedules/weeks?branchId=X
    // Lista de semanas con horarios guardados.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/weeks")
    public ResponseEntity<?> getAvailableWeeks(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long branchId) {

        boolean isSuperuser = isSuperuserContext(user);
        Long targetBranch = resolveBranch(user, branchId);
        try {
            if (isSuperuser) {
                switchTenantToBranchOwner(targetBranch);
            }
            List<LocalDate> weeks = scheduleService.getAvailableWeeks(targetBranch);
            return ResponseEntity.ok(weeks);
        } finally {
            if (isSuperuser) {
                TenantContext.setTenantId(0L);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/schedules/save
    // Guarda (reemplaza) la matriz completa de una semana.
    // Body: { branchId, weekStart, cells: [...] }
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/save")
    public ResponseEntity<?> saveRoster(
            @AuthenticationPrincipal User user,
            @RequestBody SaveRosterRequest req) {
        boolean isSuperuser = isSuperuserContext(user);
        try {
            Long targetBranch = resolveBranch(user, req.branchId());
            if (isSuperuser) {
                switchTenantToBranchOwner(targetBranch);
            }
            LocalDate weekStart = LocalDate.parse(req.weekStart());
            List<ScheduleRoster> saved = scheduleService.saveRoster(targetBranch, weekStart, req.cells());
            Map<String, Object> res = new HashMap<>();
            res.put("saved", saved.size());
            res.put("weekStart", weekStart.toString());
            res.put("branchId", targetBranch);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } finally {
            if (isSuperuser) {
                TenantContext.setTenantId(0L);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/schedules?branchId=X&weekStart=YYYY-MM-DD
    // Elimina toda la semana de una sucursal.
    // ─────────────────────────────────────────────────────────────────────────
    @DeleteMapping
    public ResponseEntity<?> deleteRoster(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        boolean isSuperuser = isSuperuserContext(user);
        Long targetBranch = resolveBranch(user, branchId);
        try {
            if (isSuperuser) {
                switchTenantToBranchOwner(targetBranch);
            }
            scheduleService.deleteRoster(targetBranch, weekStart);
            return ResponseEntity.ok(Map.of("message", "Horario eliminado correctamente"));
        } finally {
            if (isSuperuser) {
                TenantContext.setTenantId(0L);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/schedules/summary?branchId=X&weekStart=YYYY-MM-DD
    // Devuelve todos los cálculos de negocio, balance de horas, KPIs y solapamientos
    // procesados 100% en el backend.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/summary")
    public ResponseEntity<?> getScheduleSummary(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        boolean isSuperuser = isSuperuserContext(user);
        Long targetBranch = resolveBranch(user, branchId);
        try {
            if (isSuperuser) {
                switchTenantToBranchOwner(targetBranch);
            }
            ScheduleService.ScheduleSummaryDTO summary = scheduleService.calculateScheduleSummary(targetBranch, weekStart);
            return ResponseEntity.ok(summary);
        } finally {
            if (isSuperuser) {
                TenantContext.setTenantId(0L);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/schedules/individual?branchId=X&weekStart=YYYY-MM-DD
    // Devuelve los horarios individuales calculados 100% en el backend.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/individual")
    public ResponseEntity<?> getIndividualSchedules(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        boolean isSuperuser = isSuperuserContext(user);
        Long targetBranch = resolveBranch(user, branchId);
        try {
            if (isSuperuser) {
                switchTenantToBranchOwner(targetBranch);
            }
            List<ScheduleService.EmployeeIndividualScheduleDTO> individualSchedules = scheduleService.getIndividualSchedules(targetBranch, weekStart);
            return ResponseEntity.ok(individualSchedules);
        } finally {
            if (isSuperuser) {
                TenantContext.setTenantId(0L);
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean isSuperuserContext(User user) {
        Long tenantId = TenantContext.getTenantId();
        return (user != null && user.getRole() == Role.SUPERUSER) || (tenantId != null && tenantId == 0L);
    }

    private void switchTenantToBranchOwner(Long branchId) {
        Long branchTenantId = tenantSettingsService.findBranchTenantIdForSuperuser(branchId);
        if (branchTenantId != null) {
            TenantContext.setTenantId(branchTenantId);
        }
    }

    private Long resolveBranch(User user, Long requestedBranchId) {
        if (requestedBranchId != null) return requestedBranchId;
        if (user != null && user.getBranch() != null) return user.getBranch().getId();
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && tenantId == 0L) {
            var allBranches = tenantSettingsService.findAllActiveBranchesForSuperuser();
            if (!allBranches.isEmpty()) {
                return allBranches.get(0).getId();
            }
        } else if (tenantId != null) {
            var branches = branchRepository.findAll();
            if (!branches.isEmpty()) {
                return branches.get(0).getId();
            }
        }
        throw new com.checador.exception.ResourceNotFoundException("Sucursal no encontrada o no especificada para el inquilino");
    }

    private Map<String, Object> toResponse(ScheduleRoster r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("rowKey", r.getRowKey());
        m.put("areaName", r.getAreaName());
        m.put("shiftTime", r.getShiftTime());
        m.put("dayIndex", r.getDayIndex());
        m.put("employeeName", r.getEmployeeName());
        m.put("statusType", r.getStatusType().name());
        m.put("weekStart", r.getWeekStart() != null ? r.getWeekStart().toString() : null);
        m.put("branchId", r.getBranch().getId());
        m.put("shiftStartTime", r.getShiftStartTime());
        m.put("shiftEndTime", r.getShiftEndTime());
        m.put("secondShiftStartTime", r.getSecondShiftStartTime());
        m.put("secondShiftEndTime", r.getSecondShiftEndTime());
        m.put("targetArea", r.getTargetArea());
        m.put("reason", r.getReason());
        m.put("createdBy", r.getCreatedBy());
        return m;
    }

    public record SaveRosterRequest(Long branchId, String weekStart,
                                    List<Map<String, Object>> cells) {}
}
