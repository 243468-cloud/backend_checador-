package com.checador.service;

import com.checador.entity.Branch;
import com.checador.entity.ScheduleRoster;
import com.checador.entity.ScheduleRoster.RosterStatus;
import com.checador.repository.BranchRepository;
import com.checador.repository.ScheduleRosterRepository;
import com.checador.repository.UserRepository;
import com.checador.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extrae el nombre real del empleado desde el campo employeeName,
     * que puede ser un JSON stringificado ({"text":"ITZA","type":"NORMAL",...})
     * o un nombre simple como "ITZA".
     */
    private String extractRealName(String rawEmployeeName) {
        if (rawEmployeeName == null || rawEmployeeName.isBlank()) return null;
        String trimmed = rawEmployeeName.trim();
        if (trimmed.startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                JsonNode textNode = node.get("text");
                if (textNode != null && !textNode.asText().isBlank()) {
                    return textNode.asText().trim();
                }
            } catch (Exception ignored) {
                // Si falla el parseo, usar el valor crudo
            }
        }
        return trimmed;
    }

    /**
     * Extrae el statusType desde el campo employeeName JSON o devuelve el statusType
     * directo de la entidad como fallback.
     */
    private String extractStatusType(ScheduleRoster cell) {
        String rawName = cell.getEmployeeName();
        if (rawName != null && rawName.trim().startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(rawName.trim());
                JsonNode typeNode = node.get("type");
                if (typeNode != null && !typeNode.asText().isBlank()) {
                    return typeNode.asText().trim().toUpperCase();
                }
            } catch (Exception ignored) {}
        }
        return cell.getStatusType() != null ? cell.getStatusType().name() : "NORMAL";
    }

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
                    .shiftStartTime((String) cell.get("shiftStartTime"))
                    .shiftEndTime((String) cell.get("shiftEndTime"))
                    .secondShiftStartTime((String) cell.get("secondShiftStartTime"))
                    .secondShiftEndTime((String) cell.get("secondShiftEndTime"))
                    .targetArea((String) cell.get("targetArea"))
                    .reason((String) cell.get("reason"))
                    .createdBy((String) cell.get("createdBy"))
                    .build();
        }).toList();

        return rosterRepository.saveAll(roster);
    }

    /** Elimina completamente la semana de una sucursal. */
    @Transactional
    public void deleteRoster(Long branchId, LocalDate weekStart) {
        rosterRepository.deleteByBranchIdAndWeekStart(branchId, weekStart);
    }

    // ─── Lógica de Negocio y Cálculo Completo en el Backend ───────────────────

    public record EmployeeBalanceDTO(
            String name,
            String primaryArea,
            int workDays,
            int restDays,
            int doubleShifts,
            int shiftChanges,
            double totalScheduledHours,
            double actualWorkedHours,
            double overtimeHours,
            String statusBalance
    ) {}

    public record ScheduleSummaryDTO(
            int totalEmployees,
            int avgHours,
            int totalRestDays,
            int totalDoubleShifts,
            double totalOvertimeHours,
            List<EmployeeBalanceDTO> employeeBalances,
            List<String> overlapWarnings
    ) {}

    /**
     * Calcula completamente en el Backend todas las métricas de balance,
     * horas programadas, horas extra, solapamientos y KPIs del ROL semanal.
     */
    public ScheduleSummaryDTO calculateScheduleSummary(Long branchId, LocalDate weekStart) {
        List<ScheduleRoster> rosterCells = rosterRepository.findByBranchIdAndWeekStart(branchId, weekStart);

        // Agrupar por nombre real del empleado (parseando JSON si es necesario)
        Map<String, List<ScheduleRoster>> byEmployee = new java.util.HashMap<>();
        for (ScheduleRoster cell : rosterCells) {
            String name = extractRealName(cell.getEmployeeName());
            if (name != null && !name.isBlank()) {
                byEmployee.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(cell);
            }
        }

        List<EmployeeBalanceDTO> balances = new java.util.ArrayList<>();
        List<String> overlapWarnings = new java.util.ArrayList<>();

        // Detectar solapamientos (mismo empleado en distintas áreas el mismo día)
        Map<String, Map<Integer, List<String>>> empDayAreas = new java.util.HashMap<>();
        for (ScheduleRoster cell : rosterCells) {
            String realName = extractRealName(cell.getEmployeeName());
            if (realName != null && cell.getDayIndex() != null) {
                empDayAreas
                    .computeIfAbsent(realName, k -> new java.util.HashMap<>())
                    .computeIfAbsent(cell.getDayIndex(), k -> new java.util.ArrayList<>())
                    .add(cell.getAreaName());
            }
        }

        for (var entry : empDayAreas.entrySet()) {
            String emp = entry.getKey();
            for (var dayEntry : entry.getValue().entrySet()) {
                if (dayEntry.getValue().size() > 1) {
                    overlapWarnings.add("Solapamiento: " + emp + " tiene turnos en varias áreas el día " + (dayEntry.getKey() + 1));
                }
            }
        }

        for (var entry : byEmployee.entrySet()) {
            String name = entry.getKey();
            List<ScheduleRoster> cells = entry.getValue();

            String primaryArea = cells.isEmpty() ? "BARRA" : cells.get(0).getAreaName();
            java.util.Set<Integer> workDaysSet = new java.util.HashSet<>();
            java.util.Set<Integer> restDaysSet = new java.util.HashSet<>();
            int doubleShifts = 0;
            int shiftChanges = 0;
            double totalScheduledHours = 0;

            for (ScheduleRoster c : cells) {
                if (c.getStatusType() == RosterStatus.DESCANSO) {
                    restDaysSet.add(c.getDayIndex());
                } else {
                    workDaysSet.add(c.getDayIndex());

                    if (c.getStatusType() == RosterStatus.DOBLE_TURNO) doubleShifts++;
                    if (c.getStatusType() == RosterStatus.CAMBIO_TURNO) shiftChanges++;

                    // Cálculo de Horas Programadas
                    double shiftHours = 8.0; // Predeterminado
                    if (c.getShiftStartTime() != null && c.getShiftEndTime() != null &&
                        !c.getShiftStartTime().isBlank() && !c.getShiftEndTime().isBlank()) {
                        shiftHours = calculateHours(c.getShiftStartTime(), c.getShiftEndTime());
                    }
                    totalScheduledHours += shiftHours;

                    if (c.getSecondShiftStartTime() != null && c.getSecondShiftEndTime() != null &&
                        !c.getSecondShiftStartTime().isBlank() && !c.getSecondShiftEndTime().isBlank()) {
                        totalScheduledHours += calculateHours(c.getSecondShiftStartTime(), c.getSecondShiftEndTime());
                    }
                }
            }

            int workDays = workDaysSet.size();
            int restDays = restDaysSet.size();
            double overtimeHours = Math.max(0, totalScheduledHours - 48.0);

            String statusBalance = "EQUILIBRADO";
            if (totalScheduledHours > 48 || doubleShifts >= 2) {
                statusBalance = "ELEVADO";
            } else if (totalScheduledHours < 35 && workDays > 0) {
                statusBalance = "REDUCIDO";
            }

            balances.add(new EmployeeBalanceDTO(
                    name,
                    primaryArea,
                    workDays,
                    restDays,
                    doubleShifts,
                    shiftChanges,
                    Math.round(totalScheduledHours * 10.0) / 10.0,
                    Math.round(totalScheduledHours * 10.0) / 10.0,
                    Math.round(overtimeHours * 10.0) / 10.0,
                    statusBalance
            ));
        }

        int totalEmployees = balances.size();
        int avgHours = totalEmployees > 0 ? (int) Math.round(balances.stream().mapToDouble(EmployeeBalanceDTO::totalScheduledHours).sum() / totalEmployees) : 0;
        int totalRestDays = balances.stream().mapToInt(EmployeeBalanceDTO::restDays).sum();
        int totalDoubleShifts = balances.stream().mapToInt(EmployeeBalanceDTO::doubleShifts).sum();
        double totalOvertimeHours = Math.round(balances.stream().mapToDouble(EmployeeBalanceDTO::overtimeHours).sum() * 10.0) / 10.0;

        return new ScheduleSummaryDTO(
                totalEmployees,
                avgHours,
                totalRestDays,
                totalDoubleShifts,
                totalOvertimeHours,
                balances,
                overlapWarnings
        );
    }

    private double calculateHours(String startStr, String endStr) {
        try {
            java.time.LocalTime start = java.time.LocalTime.parse(startStr);
            java.time.LocalTime end = java.time.LocalTime.parse(endStr);
            long startMins = start.toSecondOfDay() / 60;
            long endMins = end.toSecondOfDay() / 60;
            if (endMins <= startMins) {
                endMins += 24 * 60;
            }
            return (endMins - startMins) / 60.0;
        } catch (Exception e) {
            return 8.0;
        }
    }

    public record DailyResolutionDTO(
            int dayIndex,
            String dayName,
            String date,
            String area,
            String shiftTime,
            String statusType,
            String displayText,
            boolean isRest
    ) {}

    public record EmployeeIndividualScheduleDTO(
            String employeeName,
            String primaryArea,
            boolean hasAssignments,
            List<DailyResolutionDTO> days
    ) {}

    /**
     * Calcula 100% en el Backend la resolución diaria y lista de horarios individuales por empleado.
     * Oculta empleados sin asignaciones (casos como Gael o Leopoldo en semanas sin turno)
     * y marca explícitamente como DESCANSO los días donde el empleado no fue asignado a ninguna casilla.
     */
    public List<EmployeeIndividualScheduleDTO> getIndividualSchedules(Long branchId, LocalDate weekStart) {
        List<ScheduleRoster> rosterCells = rosterRepository.findByBranchIdAndWeekStart(branchId, weekStart);

        // Agrupamos por nombre real del empleado (parseando JSON si es necesario)
        Map<String, List<ScheduleRoster>> byEmployee = new java.util.LinkedHashMap<>();
        for (ScheduleRoster cell : rosterCells) {
            String realName = extractRealName(cell.getEmployeeName());
            if (realName != null && !realName.isBlank()) {
                byEmployee.computeIfAbsent(realName.toUpperCase(), k -> new java.util.ArrayList<>()).add(cell);
            }
        }

        List<EmployeeIndividualScheduleDTO> result = new java.util.ArrayList<>();
        String[] dayNames = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};

        for (Map.Entry<String, List<ScheduleRoster>> entry : byEmployee.entrySet()) {
            List<ScheduleRoster> empCells = entry.getValue();

            if (empCells == null || empCells.isEmpty()) continue;

            // displayName es el nombre real parseado (sin JSON), capitalizado correctamente
            String displayName = extractRealName(empCells.get(0).getEmployeeName());
            if (displayName == null) displayName = entry.getKey();
            String primaryArea = empCells.get(0).getAreaName();

            List<DailyResolutionDTO> daysList = new java.util.ArrayList<>();

            for (int dayIdx = 0; dayIdx < 7; dayIdx++) {
                final int d = dayIdx;
                LocalDate dayDate = weekStart.plusDays(dayIdx);
                String dateStr = dayDate.getDayOfMonth() + "/" + dayDate.getMonthValue();

                ScheduleRoster matchCell = empCells.stream()
                        .filter(c -> c.getDayIndex() != null && c.getDayIndex() == d)
                        .findFirst()
                        .orElse(null);

                if (matchCell != null) {
                    // Detectar DESCANSO tanto desde statusType como desde el JSON de employeeName
                    String resolvedStatus = extractStatusType(matchCell);
                    boolean isRest = matchCell.getStatusType() == RosterStatus.DESCANSO
                            || "DESCANSO".equals(resolvedStatus)
                            || (matchCell.getReason() != null && matchCell.getReason().toUpperCase().contains("DESCANSO"));

                    // SI ES DESCANSO, no se incluye en la lista (el frontend muestra 7 días fijos)
                    if (isRest) continue;

                    String area = matchCell.getAreaName();
                    // Prioriza shiftTime de la fila, luego los campos de hora personalizados
                    String shiftTime = matchCell.getShiftTime() != null && !matchCell.getShiftTime().isBlank()
                            ? matchCell.getShiftTime()
                            : (matchCell.getShiftStartTime() != null && matchCell.getShiftEndTime() != null
                                    && !matchCell.getShiftStartTime().isBlank() && !matchCell.getShiftEndTime().isBlank()
                            ? matchCell.getShiftStartTime() + "-" + matchCell.getShiftEndTime()
                            : "Turno Regular");

                    daysList.add(new DailyResolutionDTO(
                            d,
                            dayNames[d],
                            dateStr,
                            area,
                            shiftTime,
                            resolvedStatus,
                            area + " (" + shiftTime + ")",
                            false
                    ));
                }
            }

            // Solo incluir al empleado si tiene días de trabajo efectivamente asignados
            if (!daysList.isEmpty()) {
                result.add(new EmployeeIndividualScheduleDTO(
                        displayName,
                        primaryArea,
                        true,
                        daysList
                ));
            }
        }

        return result;
    }
}
