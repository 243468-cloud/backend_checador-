package com.checador.service;

import com.checador.entity.Branch;
import com.checador.entity.ScheduleRoster;
import com.checador.entity.ScheduleRoster.RosterStatus;
import com.checador.repository.BranchRepository;
import com.checador.repository.ScheduleRosterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRosterRepository rosterRepository;
    private final BranchRepository branchRepository;

    // ─── Lectura ──────────────────────────────────────────────────────────────

    /**
     * Devuelve todas las celdas de la matriz semanal.
     * El frontend reconstruye la grilla a partir de esta lista.
     */
    public List<ScheduleRoster> getRoster(Long branchId, LocalDate weekStart) {
        return rosterRepository.findByBranchIdAndWeekStart(branchId, weekStart);
    }

    /** Semanas que ya tienen datos guardados para el selector de semana. */
    public List<LocalDate> getAvailableWeeks(Long branchId) {
        return rosterRepository.findDistinctWeekStartsByBranchId(branchId);
    }

    // ─── Escritura ────────────────────────────────────────────────────────────

    /**
     * Recibe la matriz completa desde el frontend (lista de celdas)
     * y reemplaza atómicamente toda la semana de esa sucursal.
     *
     * @param branchId  ID de la sucursal.
     * @param weekStart Lunes de la semana (ej. 2026-08-24).
     * @param cells     Lista de mapas con los campos de cada celda.
     */
    @Transactional
    public List<ScheduleRoster> saveRoster(Long branchId, LocalDate weekStart,
                                            List<Map<String, Object>> cells) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada: " + branchId));

        // Eliminar la semana existente y reemplazarla completamente
        rosterRepository.deleteByBranchIdAndWeekStart(branchId, weekStart);

        List<ScheduleRoster> roster = cells.stream().map(cell -> {
            String statusStr = (String) cell.getOrDefault("statusType", "NORMAL");
            RosterStatus status;
            try {
                status = RosterStatus.valueOf(statusStr.toUpperCase().replace(" ", "_"));
            } catch (IllegalArgumentException e) {
                status = RosterStatus.NORMAL;
            }

            return ScheduleRoster.builder()
                    .branch(branch)
                    .rowKey((String) cell.get("rowKey"))
                    .areaName((String) cell.getOrDefault("areaName", ""))
                    .shiftTime((String) cell.getOrDefault("shiftTime", ""))
                    .dayIndex(((Number) cell.get("dayIndex")).intValue())
                    .employeeName((String) cell.getOrDefault("employeeName", ""))
                    .statusType(status)
                    .weekStart(weekStart)
                    .build();
        }).toList();

        return rosterRepository.saveAll(roster);
    }

    /** Elimina completamente la semana de una sucursal. */
    @Transactional
    public void deleteRoster(Long branchId, LocalDate weekStart) {
        rosterRepository.deleteByBranchIdAndWeekStart(branchId, weekStart);
    }
}
