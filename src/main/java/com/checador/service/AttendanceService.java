package com.checador.service;

import com.checador.entity.Attendance;
import com.checador.entity.AttendanceStatus;
import com.checador.entity.Branch;
import com.checador.entity.ShiftType;
import com.checador.entity.User;
import com.checador.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final GeoService geoService;

    @Lazy
    private final NotificationService notificationService;

    // ─── Horarios de turnos ───────────────────────────────────────────────────
    private static final LocalTime MORNING_START   = LocalTime.of(7, 0);
    private static final LocalTime MORNING_END     = LocalTime.of(15, 0);
    private static final LocalTime EVENING_START   = LocalTime.of(15, 0);
    private static final LocalTime EVENING_END     = LocalTime.of(23, 0);
    private static final LocalTime SUNDAY_START    = LocalTime.of(8, 0);
    private static final LocalTime SUNDAY_END      = LocalTime.of(18, 0);
    private static final LocalTime MIXED_START     = LocalTime.of(11, 0);
    private static final LocalTime MIXED_END       = LocalTime.of(19, 0);

    /**
     * Registra la entrada del empleado con validación de geolocalización.
     */
    @Transactional
    public Attendance checkIn(User employee, Branch branch, double lat, double lng) {
        LocalDate today = LocalDate.now();

        // 1. Verificar si ya tiene un turno activo sin cerrar
        Optional<Attendance> activeOpt = attendanceRepository
                .findTopByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(employee.getId());
        if (activeOpt.isPresent()) {
            throw new IllegalStateException("Ya tienes una entrada activa registrada. Debes marcar salida antes de iniciar un nuevo turno.");
        }

        // 2. Verificar que no haya completado ya su jornada hoy
        if (attendanceRepository.existsByUserIdAndAttendanceDate(employee.getId(), today)) {
            throw new IllegalStateException("Ya completaste tu registro de asistencia de hoy.");
        }

        // Validar geolocalización
        validateLocation(branch, lat, lng);

        LocalDateTime now = LocalDateTime.now();
        ShiftType shift = employee.getShiftType();
        LocalTime scheduledStart = getShiftStart(shift);
        int toleranceMinutes = branch.getToleranceMinutes();

        // Calcular tardanza
        LocalTime nowTime = now.toLocalTime();
        int lateMinutes = 0;
        AttendanceStatus status = AttendanceStatus.ON_TIME;

        if (nowTime.isAfter(scheduledStart.plusMinutes(toleranceMinutes))) {
            lateMinutes = (int) java.time.Duration.between(scheduledStart, nowTime).toMinutes();
            status = AttendanceStatus.LATE;
        }

        Attendance attendance = Attendance.builder()
                .user(employee)
                .branch(branch)
                .attendanceDate(today)
                .shiftType(shift)
                .checkInTime(now)
                .checkInLatitude(lat)
                .checkInLongitude(lng)
                .status(status)
                .lateMinutes(lateMinutes)
                .build();

        Attendance saved = attendanceRepository.save(attendance);
        notificationService.notifyCheckIn(saved);
        return saved;
    }

    /**
     * Registra la salida del empleado con validación de geolocalización.
     * Funciona desde cualquier dispositivo donde el empleado inicie sesión.
     */
    @Transactional
    public Attendance checkOut(User employee, Branch branch, double lat, double lng) {
        LocalDate today = LocalDate.now();

        // Buscar el turno activo sin cerrar (independientemente del dispositivo usado)
        Attendance attendance = attendanceRepository
                .findTopByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(employee.getId())
                .orElseGet(() -> attendanceRepository
                        .findByUserIdAndAttendanceDate(employee.getId(), today)
                        .orElse(null));

        if (attendance == null) {
            throw new IllegalStateException("No tienes una entrada activa para marcar salida.");
        }

        if (attendance.getCheckOutTime() != null) {
            throw new IllegalStateException("Ya registraste tu salida para este turno.");
        }

        // Validar geolocalización física del dispositivo actual al marcar salida
        validateLocation(branch, lat, lng);

        attendance.setCheckOutTime(LocalDateTime.now());
        attendance.setCheckOutLatitude(lat);
        attendance.setCheckOutLongitude(lng);
        attendance.calculateHoursWorked();

        // Actualizar estado si era IN_SHIFT
        if (attendance.getStatus() == AttendanceStatus.IN_SHIFT) {
            attendance.setStatus(AttendanceStatus.ON_TIME);
        }

        Attendance saved = attendanceRepository.save(attendance);
        notificationService.notifyCheckOut(saved);
        return saved;
    }

    /**
     * Obtiene el estado actual del empleado (mantiene el turno activo al cambiar de dispositivo).
     */
    public Optional<Attendance> getTodayAttendance(Long userId) {
        // 1. Si hay un turno activo abierto (checkIn sin checkOut), retornar ese turno activo
        Optional<Attendance> active = attendanceRepository
                .findTopByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(userId);
        if (active.isPresent()) {
            return active;
        }
        // 2. Si no hay turno abierto, retornar el registro de hoy
        return attendanceRepository.findByUserIdAndAttendanceDate(userId, LocalDate.now());
    }

    /**
     * Historial de asistencia del empleado por mes.
     */
    public List<Attendance> getMonthlyAttendance(Long userId, int year, int month) {
        return attendanceRepository.findMonthlyAttendanceByUser(userId, year, month);
    }

    /**
     * Asistencia diaria de la sucursal (para dashboard admin).
     */
    public List<Attendance> getDailyAttendanceByBranch(Long branchId, LocalDate date) {
        return attendanceRepository.findDailyAttendanceByBranch(branchId, date);
    }

    /**
     * Asistencia mensual de la sucursal (para reportes admin).
     */
    public List<Attendance> getMonthlyAttendanceByBranch(Long branchId, int year, int month) {
        return attendanceRepository.findMonthlyAttendanceByBranch(branchId, year, month);
    }

    /**
     * KPIs del día para dashboard admin.
     */
    public DashboardStats getDashboardStats(Long branchId, LocalDate date) {
        long onTime = attendanceRepository.countOnTimeByBranchAndDate(branchId, date);
        long late = attendanceRepository.countLateByBranchAndDate(branchId, date);
        long absent = attendanceRepository.countAbsentByBranchAndDate(branchId, date);
        return new DashboardStats(onTime, late, absent);
    }

    /**
     * Actualiza un registro de asistencia (solo admin).
     */
    @Transactional
    public Attendance updateAttendance(Long id, LocalDateTime checkIn, LocalDateTime checkOut,
                                       AttendanceStatus status, String notes) {
        Attendance a = attendanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Registro no encontrado"));
        if (checkIn != null) a.setCheckInTime(checkIn);
        if (checkOut != null) a.setCheckOutTime(checkOut);
        if (status != null) a.setStatus(status);
        if (notes != null) a.setNotes(notes);
        a.calculateHoursWorked();
        return attendanceRepository.save(a);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void validateLocation(Branch branch, double lat, double lng) {
        double distance = geoService.calculateDistance(
                branch.getLatitude(), branch.getLongitude(), lat, lng);
        if (distance > branch.getRadiusMeters()) {
            throw new IllegalStateException(
                    String.format("Estás fuera del área permitida. Distancia: %.0fm, Permitido: %dm",
                            distance, branch.getRadiusMeters()));
        }
    }

    private LocalTime getShiftStart(ShiftType shift) {
        return switch (shift) {
            case MORNING -> MORNING_START;
            case EVENING -> EVENING_START;
            case SUNDAY  -> SUNDAY_START;
            case MIXED   -> MIXED_START;
        };
    }

    public record DashboardStats(long onTime, long late, long absent) {}
}
