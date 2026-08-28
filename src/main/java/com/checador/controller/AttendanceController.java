package com.checador.controller;

import com.checador.entity.Attendance;
import com.checador.entity.AttendanceStatus;
import com.checador.entity.Branch;
import com.checador.entity.User;
import com.checador.service.AttendanceService;
import com.checador.service.BranchService;
import com.checador.service.ReportService;
import com.checador.service.UserService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final UserService userService;
    private final BranchService branchService;
    private final ReportService reportService;

    // ─── Endpoints para EMPLEADO ──────────────────────────────────────────────

    @PostMapping("/checkin")
    public ResponseEntity<?> checkIn(@AuthenticationPrincipal User employee,
                                      @RequestBody CheckRequest req) {
        try {
            Branch branch = branchService.findById(employee.getBranch().getId());
            Attendance a = attendanceService.checkIn(employee, branch, req.latitude(), req.longitude());
            return ResponseEntity.ok(toResponse(a));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkOut(@AuthenticationPrincipal User employee,
                                       @RequestBody CheckRequest req) {
        try {
            Branch branch = branchService.findById(employee.getBranch().getId());
            Attendance a = attendanceService.checkOut(employee, branch, req.latitude(), req.longitude());
            return ResponseEntity.ok(toResponse(a));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/today")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getTodayStatus(@AuthenticationPrincipal User user) {
        try {
            Optional<Attendance> attendance = attendanceService.getTodayAttendance(user.getId());
            return ResponseEntity.ok(attendance.map(this::toResponse).orElse(null));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error consultando estado de hoy: " + e.getMessage()));
        }
    }

    @GetMapping("/my-history")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getMyMonthlyHistory(@AuthenticationPrincipal User employee,
                                                  @RequestParam int year,
                                                  @RequestParam int month) {
        try {
            List<Attendance> records = attendanceService.getMonthlyAttendance(employee.getId(), year, month);
            return ResponseEntity.ok(records.stream().map(this::toResponse).toList());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error consultando historial: " + e.getMessage()));
        }
    }

    // ─── Endpoints para ADMIN ─────────────────────────────────────────────────

    private static final java.time.ZoneId MEXICO_ZONE = java.time.ZoneId.of("America/Mexico_City");

    @GetMapping("/admin/daily")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getDailyByBranch(@AuthenticationPrincipal User admin,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now(MEXICO_ZONE);
        Long branchId = admin.getBranch() != null ? admin.getBranch().getId() : null;
        List<Attendance> records = attendanceService.getDailyAttendanceByBranch(branchId, target);
        return ResponseEntity.ok(records.stream().map(this::toResponse).toList());
    }

    @GetMapping("/admin/monthly")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getMonthlyByBranch(@AuthenticationPrincipal User admin,
                                                  @RequestParam int year,
                                                  @RequestParam int month) {
        Long branchId = admin.getBranch() != null ? admin.getBranch().getId() : null;
        List<Attendance> records = attendanceService.getMonthlyAttendanceByBranch(branchId, year, month);
        return ResponseEntity.ok(records.stream().map(this::toResponse).toList());
    }

    @GetMapping("/admin/stats")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getDashboardStats(@AuthenticationPrincipal User admin,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now(MEXICO_ZONE);
        Long branchId = admin.getBranch() != null ? admin.getBranch().getId() : null;
        AttendanceService.DashboardStats stats = attendanceService.getDashboardStats(branchId, target);
        Map<String, Object> res = new java.util.HashMap<>();
        res.put("onTime", stats.onTime());
        res.put("late", stats.late());
        res.put("absent", stats.absent());
        res.put("date", target.toString());
        return ResponseEntity.ok(res);
    }

    @PutMapping("/admin/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> updateAttendance(@PathVariable Long id,
                                               @RequestBody UpdateRequest req) {
        try {
            Attendance a = attendanceService.updateAttendance(
                    id, req.checkInTime(), req.checkOutTime(), req.status(), req.notes(), req.lateMinutes());
            return ResponseEntity.ok(toResponse(a));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/admin/payroll")
    public ResponseEntity<?> downloadPayroll(@AuthenticationPrincipal User admin,
                                              @RequestParam int year,
                                              @RequestParam int month) {
        try {
            Long branchId   = admin.getBranch() != null ? admin.getBranch().getId() : null;
            String branch   = admin.getBranch() != null ? admin.getBranch().getName() : "Todas";
            List<Attendance> records = attendanceService.getMonthlyAttendanceByBranch(branchId, year, month);
            byte[] excel = reportService.generatePayrollReport(records, branch, month, year);

            String filename = "prenomina_" + month + "_" + year + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excel);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error generando Pre-Nómina: " + e.getMessage()));
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Map<String, Object> toResponse(Attendance a) {
        Map<String, Object> res = new java.util.HashMap<>();
        res.put("id", a.getId());
        res.put("employeeId", a.getUser().getId());
        res.put("employeeName", a.getUser().getFullName());
        res.put("date", a.getAttendanceDate().toString());
        res.put("shift", a.getShiftType() != null ? a.getShiftType().name() : "");
        res.put("checkIn", a.getCheckInTime() != null ? a.getCheckInTime().toString() : "");
        res.put("checkOut", a.getCheckOutTime() != null ? a.getCheckOutTime().toString() : "");
        res.put("status", a.getStatus().name());
        res.put("lateMinutes", a.getLateMinutes() != null ? a.getLateMinutes() : 0);
        res.put("hoursWorked", a.getHoursWorked() != null ? a.getHoursWorked() : 0);
        res.put("notes", a.getNotes() != null ? a.getNotes() : "");
        return res;
    }

    public record CheckRequest(@NotNull Double latitude, @NotNull Double longitude) {}
    public record UpdateRequest(LocalDateTime checkInTime, LocalDateTime checkOutTime,
                                AttendanceStatus status, String notes, Integer lateMinutes) {}
}
