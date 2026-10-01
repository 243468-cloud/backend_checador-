package com.checador.controller;

import com.checador.entity.Attendance;
import com.checador.entity.User;
import com.checador.service.AttendanceService;
import com.checador.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final AttendanceService attendanceService;
    private final ReportService reportService;

    private Long getBranchIdSafely(User u) {
        if (u == null) return null;
        try {
            return u.getBranch() != null ? u.getBranch().getId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getBranchNameSafely(User u) {
        if (u == null) return "Todas las sucursales";
        try {
            return u.getBranch() != null ? u.getBranch().getName() : "Todas las sucursales";
        } catch (Exception e) {
            return "Todas las sucursales";
        }
    }

    /**
     * Exportar Excel mensual de la sucursal del admin.
     */
    @GetMapping("/excel")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadExcel(@AuthenticationPrincipal User admin,
                                                 @RequestParam int year,
                                                 @RequestParam int month,
                                                 @RequestParam(required = false) String periodType,
                                                 @RequestParam(required = false) Integer subPeriod) throws IOException {
        Long branchId = getBranchIdSafely(admin);
        String branchName = getBranchNameSafely(admin);
        List<Attendance> records = attendanceService.getMonthlyAttendanceByBranch(branchId, year, month);

        String periodLabel = month + "/" + year;
        if (periodType != null && subPeriod != null && !"MONTHLY".equals(periodType)) {
            if ("BIWEEKLY".equals(periodType)) periodLabel = "Quincena " + subPeriod + " de " + month + "/" + year;
            else if ("WEEKLY".equals(periodType)) periodLabel = "Semana " + subPeriod + " de " + month + "/" + year;

            records = records.stream().filter(a -> {
                int day = a.getAttendanceDate().getDayOfMonth();
                if ("BIWEEKLY".equals(periodType)) {
                    if (subPeriod == 1) return day >= 1 && day <= 15;
                    if (subPeriod == 2) return day >= 16;
                } else if ("WEEKLY".equals(periodType)) {
                    if (subPeriod == 1) return day >= 1 && day <= 7;
                    if (subPeriod == 2) return day >= 8 && day <= 14;
                    if (subPeriod == 3) return day >= 15 && day <= 21;
                    if (subPeriod == 4) return day >= 22;
                }
                return true;
            }).toList();
        }

        byte[] excel = reportService.generateExcelReport(records, branchName, month, year, periodLabel);

        String filename = String.format("asistencia-%s-%d-%02d.xlsx", branchName.replace(" ", "_"), year, month);
        if (periodType != null && subPeriod != null && !"MONTHLY".equals(periodType)) {
             filename = String.format("asistencia-%s-%s-%d-%d-%02d.xlsx", branchName.replace(" ", "_"), periodType.toLowerCase(), subPeriod, year, month);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    /**
     * Datos de reporte mensual en JSON (para gráficas frontend).
     */
    @GetMapping("/monthly")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMonthlyData(@AuthenticationPrincipal User admin,
                                             @RequestParam int year,
                                             @RequestParam int month) {
        Long branchId = getBranchIdSafely(admin);
        List<Attendance> records = attendanceService.getMonthlyAttendanceByBranch(branchId, year, month);
        return ResponseEntity.ok(records.stream().map(a -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", a.getId());
            map.put("employeeId", a.getUser().getId());
            map.put("employeeName", a.getUser().getFullName());
            map.put("date", a.getAttendanceDate().toString());
            map.put("shift", a.getShiftType().name());
            map.put("checkIn", a.getCheckInTime() != null ? a.getCheckInTime().toString() : "");
            map.put("checkOut", a.getCheckOutTime() != null ? a.getCheckOutTime().toString() : "");
            map.put("status", a.getStatus().name());
            map.put("lateMinutes", a.getLateMinutes() != null ? a.getLateMinutes() : 0);
            map.put("hoursWorked", a.getActualHoursWorked());
            return map;
        }).toList());
    }

    /**
     * Reporte global de todas las sucursales (solo Superusuario).
     */
    @GetMapping("/global/summary")
    public ResponseEntity<?> getGlobalSummary(@RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(Map.of("message", "Global report", "year", year, "month", month));
    }
}
