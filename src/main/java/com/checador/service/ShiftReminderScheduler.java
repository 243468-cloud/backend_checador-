package com.checador.service;

import com.checador.entity.*;
import com.checador.repository.AttendanceRepository;
import com.checador.repository.NotificationRepository;
import com.checador.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftReminderScheduler {

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final NotificationRepository notificationRepository;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Cron Job running every 60 seconds to check 15-minute shift entrance/exit reminders.
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void processShiftReminders() {
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now().withSecond(0).withNano(0);

        List<User> activeEmployees = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.EMPLOYEE && Boolean.TRUE.equals(u.getActive()))
                .toList();

        for (User emp : activeEmployees) {
            ShiftType shift = emp.getShiftType();
            if (shift == null) continue;

            LocalTime startTime = getShiftStartTime(shift);
            LocalTime endTime = getShiftEndTime(shift);

            if (startTime == null || endTime == null) continue;

            // 1. Check-In Reminder (15 Minutes Before Start Time)
            LocalTime checkInReminderTime = startTime.minusMinutes(15);
            if (nowTime.equals(checkInReminderTime)) {
                sendCheckInReminderIfNotCheckedIn(emp, today, shift, startTime);
            }

            // 2. Check-Out Reminder (15 Minutes Before End Time)
            LocalTime checkOutReminderTime = endTime.minusMinutes(15);
            if (nowTime.equals(checkOutReminderTime)) {
                sendCheckOutReminderIfNotCheckedOut(emp, today, shift, endTime);
            }

            // 3. Unregistered Exit Penalty Check (1 Minute After Shift End Time - Zero Tolerance)
            LocalTime unregisteredCheckOutDeadline = endTime.plusMinutes(1);
            if (nowTime.equals(unregisteredCheckOutDeadline)) {
                flagUnregisteredCheckOutAsLate(emp, today, shift);
            }
        }
    }

    private void sendCheckInReminderIfNotCheckedIn(User emp, LocalDate today, ShiftType shift, LocalTime startTime) {
        Optional<Attendance> attOpt = attendanceRepository.findByUserIdAndAttendanceDate(emp.getId(), today);
        boolean alreadyCheckedIn = attOpt.isPresent() && attOpt.get().getCheckInTime() != null;

        if (!alreadyCheckedIn) {
            String title = "⏰ Recordatorio de Entrada — Vía Gourmet";
            String body = "Hola " + emp.getFullName() + ", tu turno " + translateShift(shift) +
                    " inicia en 15 minutos (" + startTime.format(TIME_FMT) + "). No olvides registrar tu ENTRADA a tiempo.";

            saveReminderNotification(emp, title, body, "⏰");
            log.info("Check-in reminder sent to employee ID: {} ({})", emp.getId(), emp.getFullName());
        }
    }

    private void sendCheckOutReminderIfNotCheckedOut(User emp, LocalDate today, ShiftType shift, LocalTime endTime) {
        Optional<Attendance> attOpt = attendanceRepository.findByUserIdAndAttendanceDate(emp.getId(), today);
        boolean checkedInAndNotCheckedOut = attOpt.isPresent()
                && attOpt.get().getCheckInTime() != null
                && attOpt.get().getCheckOutTime() == null;

        if (checkedInAndNotCheckedOut) {
            String title = "🔔 Recordatorio de Salida — Vía Gourmet";
            String body = "Hola " + emp.getFullName() + ", tu turno " + translateShift(shift) +
                    " finaliza en 15 minutos (" + endTime.format(TIME_FMT) + "). Recuerda registrar tu SALIDA al concluir.";

            saveReminderNotification(emp, title, body, "🔔");
            log.info("Check-out reminder sent to employee ID: {} ({})", emp.getId(), emp.getFullName());
        }
    }

    private void flagUnregisteredCheckOutAsLate(User emp, LocalDate today, ShiftType shift) {
        Optional<Attendance> attOpt = attendanceRepository.findByUserIdAndAttendanceDate(emp.getId(), today);
        if (attOpt.isPresent()) {
            Attendance att = attOpt.get();
            if (att.getCheckInTime() != null && att.getCheckOutTime() == null) {
                att.setStatus(AttendanceStatus.LATE);
                att.setLateMinutes((att.getLateMinutes() != null ? att.getLateMinutes() : 0) + 30);
                att.setNotes("Salida no registrada a tiempo (Sin tolerancia en salida). Marcado automáticamente como Retardo.");
                attendanceRepository.save(att);

                String title = "⚠️ Salida No Registrada — Marcado con Retardo";
                String body = "Hola " + emp.getFullName() + ", no registraste tu SALIDA en el turno " + translateShift(shift) +
                        " al finalizar tu jornada. Tu registro fue marcado con Retardo.";

                saveReminderNotification(emp, title, body, "⚠️");
                log.info("Unregistered check-out flagged as LATE for employee ID: {} ({})", emp.getId(), emp.getFullName());
            }
        }
    }

    private void saveReminderNotification(User emp, String title, String body, String icon) {
        Long branchId = emp.getBranch() != null ? emp.getBranch().getId() : null;

        // Notification for Employee
        Notification notifEmp = Notification.builder()
                .recipientRole("EMPLOYEE")
                .branchId(branchId)
                .type(Notification.NotifType.SHIFT_REMINDER)
                .title(title)
                .body(body)
                .icon(icon)
                .build();
        notificationRepository.save(notifEmp);

        // Notification for Admin/Superuser monitoring
        Notification notifAdmin = Notification.builder()
                .recipientRole("ADMIN")
                .branchId(branchId)
                .type(Notification.NotifType.SHIFT_REMINDER)
                .title(title)
                .body(body)
                .icon(icon)
                .build();
        notificationRepository.save(notifAdmin);
    }

    private LocalTime getShiftStartTime(ShiftType shift) {
        return switch (shift) {
            case MORNING -> LocalTime.of(7, 0);
            case EVENING -> LocalTime.of(15, 0);
            case SUNDAY -> LocalTime.of(8, 0);
            case MIXED -> LocalTime.of(11, 0);
        };
    }

    private LocalTime getShiftEndTime(ShiftType shift) {
        return switch (shift) {
            case MORNING -> LocalTime.of(15, 0);
            case EVENING -> LocalTime.of(23, 0);
            case SUNDAY -> LocalTime.of(18, 0);
            case MIXED -> LocalTime.of(19, 0);
        };
    }

    private String translateShift(ShiftType shift) {
        return switch (shift) {
            case MORNING -> "Matutino (7:00 – 15:00)";
            case EVENING -> "Vespertino (15:00 – 23:00)";
            case SUNDAY -> "Dominical (8:00 – 18:00)";
            case MIXED -> "Mixto (11:00 – 19:00)";
        };
    }
}
