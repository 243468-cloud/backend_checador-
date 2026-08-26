package com.checador.service;

import com.checador.entity.Attendance;
import com.checador.entity.Notification;
import com.checador.entity.Notification.NotifType;
import com.checador.entity.User;
import com.checador.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifRepo;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_NOTIFS = 50;

    // ─── Creación de notificaciones de asistencia ─────────────────────────────

    @Transactional
    public void notifyCheckIn(Attendance a) {
        User emp    = a.getUser();
        String time = a.getCheckInTime().format(TIME_FMT);
        String late = a.getLateMinutes() != null && a.getLateMinutes() > 0
                ? " (retardo: " + a.getLateMinutes() + " min)"
                : "";
        String branchName = a.getBranch() != null ? a.getBranch().getName() : "—";

        String title = "✅ " + emp.getFullName() + " — Entrada registrada";
        String body  = "Hora de entrada: " + time + late
                + "\nSucursal: " + branchName
                + "\nTurno: " + translateShift(a.getShiftType());

        // Notificar a ADMIN de la sucursal
        createNotif("ADMIN", a.getBranch() != null ? a.getBranch().getId() : null,
                NotifType.CHECK_IN, title, body, "🟢");

        // Notificar a SUPERUSER (sin restricción de sucursal → branchId = null)
        createNotif("SUPERUSER", null,
                NotifType.CHECK_IN, title, body, "🟢");
    }

    @Transactional
    public void notifyCheckOut(Attendance a) {
        User emp       = a.getUser();
        String timeIn  = a.getCheckInTime() != null  ? a.getCheckInTime().format(TIME_FMT)  : "—";
        String timeOut = a.getCheckOutTime() != null ? a.getCheckOutTime().format(TIME_FMT) : "—";
        String hours   = a.getHoursWorked()  != null ? String.format("%.1f h", a.getHoursWorked()) : "—";
        String branchName = a.getBranch() != null ? a.getBranch().getName() : "—";

        String title = "🔴 " + emp.getFullName() + " — Salida registrada";
        String body  = "Entrada: " + timeIn + "  |  Salida: " + timeOut
                + "\nHoras trabajadas: " + hours
                + "\nSucursal: " + branchName;

        createNotif("ADMIN", a.getBranch() != null ? a.getBranch().getId() : null,
                NotifType.CHECK_OUT, title, body, "🔴");

        createNotif("SUPERUSER", null,
                NotifType.CHECK_OUT, title, body, "🔴");
    }

    // ─── Consultas ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Notification> getRecent(String role, Long branchId) {
        return notifRepo.findForRole(role, branchId, PageRequest.of(0, MAX_NOTIFS));
    }

    @Transactional(readOnly = true)
    public long countUnread(String role, Long branchId) {
        return notifRepo.countUnread(role, branchId);
    }

    @Transactional
    public void markAllRead(String role, Long branchId) {
        notifRepo.markAllRead(role, branchId);
    }

    @Transactional
    public void markOneRead(Long id) {
        notifRepo.findById(id).ifPresent(n -> {
            if (!n.isRead()) {
                n.setReadAt(java.time.LocalDateTime.now());
                notifRepo.save(n);
            }
        });
    }

    // ─── Helper privado ───────────────────────────────────────────────────────

    private void createNotif(String role, Long branchId,
                             NotifType type, String title, String body, String icon) {
        notifRepo.save(Notification.builder()
                .recipientRole(role)
                .branchId(branchId)
                .type(type)
                .title(title)
                .body(body)
                .icon(icon)
                .build());
    }

    private String translateShift(com.checador.entity.ShiftType s) {
        if (s == null) return "—";
        return switch (s) {
            case MORNING -> "Matutino (7:00–15:00)";
            case EVENING -> "Vespertino (15:00–23:00)";
            case SUNDAY  -> "Dominical (8:00–18:00)";
            case MIXED   -> "Mixto (11:00–19:00)";
        };
    }
}
