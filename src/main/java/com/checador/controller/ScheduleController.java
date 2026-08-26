package com.checador.controller;

import com.checador.entity.ScheduleRoster;
import com.checador.entity.User;
import com.checador.service.ScheduleService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/schedules?branchId=X&weekStart=YYYY-MM-DD
    // Carga la matriz semanal de una sucursal.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<?> getRoster(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {

        Long targetBranch = resolveBranch(user, branchId);
        List<ScheduleRoster> roster = scheduleService.getRoster(targetBranch, weekStart);
        return ResponseEntity.ok(roster.stream().map(this::toResponse).toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/schedules/weeks?branchId=X
    // Lista de semanas con horarios guardados.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/weeks")
    public ResponseEntity<?> getAvailableWeeks(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long branchId) {

        Long targetBranch = resolveBranch(user, branchId);
        List<LocalDate> weeks = scheduleService.getAvailableWeeks(targetBranch);
        return ResponseEntity.ok(weeks);
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
        try {
            Long targetBranch = resolveBranch(user, req.branchId());
            LocalDate weekStart = LocalDate.parse(req.weekStart());
            List<ScheduleRoster> saved = scheduleService.saveRoster(targetBranch, weekStart, req.cells());
            Map<String, Object> res = new HashMap<>();
            res.put("saved", saved.size());
            res.put("weekStart", weekStart.toString());
            res.put("branchId", targetBranch);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
        Long targetBranch = resolveBranch(user, branchId);
        scheduleService.deleteRoster(targetBranch, weekStart);
        return ResponseEntity.ok(Map.of("message", "Horario eliminado correctamente"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Long resolveBranch(User user, Long requestedBranchId) {
        if (requestedBranchId != null) return requestedBranchId;
        if (user.getBranch() != null) return user.getBranch().getId();
        throw new RuntimeException("Se requiere branchId");
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
        return m;
    }

    public record SaveRosterRequest(Long branchId, String weekStart,
                                    List<Map<String, Object>> cells) {}
}
