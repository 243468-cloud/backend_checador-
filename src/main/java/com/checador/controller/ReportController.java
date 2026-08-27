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
                                                 @RequestParam int month) throws IOException {
        Long branchId = getBranchIdSafely(admin);
        String branchName = getBranchNameSafely(admin);
        List<Attendance> records = attendanceService.getMonthlyAttendanceByBranch(branchId, year, month);
        byte[] excel = reportService.generateExcelReport(records, branchName, month, year);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        String.format("attachment; filename=asistencia-%s-%d-%02d.xlsx", branchName.replace(" ", "_"), year, month))
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
