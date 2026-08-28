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
    private final RealtimeEventService realtimeEventService;
    private final WebPushService webPushService;

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
        Long branchId = a.getBranch() != null ? a.getBranch().getId() : null;

        String title = "✅ " + emp.getFullName() + " — Entrada registrada";
        String body  = "Hora de entrada: " + time + late
                + "\nSucursal: " + branchName
                + "\nTurno: " + translateShift(a.getShiftType());

        // Emitir evento SSE instantáneo para refrescar tablas de asistencia
        realtimeEventService.broadcastEvent("CHECK_IN", a, "ADMIN", branchId);
        realtimeEventService.broadcastEvent("CHECK_IN", a, "SUPERUSER", null);

        // Notificar a ADMIN de la sucursal
        createNotif("ADMIN", branchId, NotifType.CHECK_IN, title, body, "🟢");

        // Notificar a SUPERUSER (sin restricción de sucursal → branchId = null)
        createNotif("SUPERUSER", null, NotifType.CHECK_IN, title, body, "🟢");
    }

    @Transactional
    public void notifyCheckOut(Attendance a) {
        User emp       = a.getUser();
        String timeIn  = a.getCheckInTime() != null  ? a.getCheckInTime().format(TIME_FMT)  : "—";
        String timeOut = a.getCheckOutTime() != null ? a.getCheckOutTime().format(TIME_FMT) : "—";
        String hours   = a.getHoursWorked()  != null ? String.format("%.1f h", a.getHoursWorked()) : "—";
        String branchName = a.getBranch() != null ? a.getBranch().getName() : "—";
        Long branchId = a.getBranch() != null ? a.getBranch().getId() : null;

        String title = "🔴 " + emp.getFullName() + " — Salida registrada";
        String body  = "Entrada: " + timeIn + "  |  Salida: " + timeOut
                + "\nHoras trabajadas: " + hours
                + "\nSucursal: " + branchName;

        // Emitir evento SSE instantáneo
        realtimeEventService.broadcastEvent("CHECK_OUT", a, "ADMIN", branchId);
        realtimeEventService.broadcastEvent("CHECK_OUT", a, "SUPERUSER", null);

        createNotif("ADMIN", branchId, NotifType.CHECK_OUT, title, body, "🔴");
        createNotif("SUPERUSER", null, NotifType.CHECK_OUT, title, body, "🔴");
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void notifySecurityAlert(User employee, String details) {
        String empName = employee != null ? employee.getFullName() : "Empleado";
        String username = employee != null ? employee.getUsername() : "desconocido";
        Long branchId = (employee != null && employee.getBranch() != null) ? employee.getBranch().getId() : null;
        String branchName = (employee != null && employee.getBranch() != null) ? employee.getBranch().getName() : "Todas";
        String time = java.time.LocalDateTime.now(java.time.ZoneId.of("America/Mexico_City")).format(TIME_FMT);

        String title = "🚨 ALERTA DE SEGURIDAD: " + empName;
        String body = "El usuario @" + username + " (" + empName + ") " + details + " a las " + time + "."
                + "\nSucursal: " + branchName;

        // Notificar a ADMIN de la sucursal
        createNotif("ADMIN", branchId, NotifType.SECURITY_ALERT, title, body, "🚨");

        // Notificar a SUPERUSER (global)
        createNotif("SUPERUSER", null, NotifType.SECURITY_ALERT, title, body, "🚨");
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
        realtimeEventService.broadcastEvent("NOTIFICATIONS_READ", "all", role, branchId);
    }

    @Transactional
    public void markOneRead(Long id) {
        notifRepo.findById(id).ifPresent(n -> {
            if (!n.isRead()) {
                n.setReadAt(java.time.LocalDateTime.now(java.time.ZoneId.of("America/Mexico_City")));
                notifRepo.save(n);
                realtimeEventService.broadcastEvent("NOTIFICATIONS_READ", n.getId(), n.getRecipientRole(), n.getBranchId());
            }
        });
    }

    // ─── Helper privado ───────────────────────────────────────────────────────

    private void createNotif(String role, Long branchId,
                             NotifType type, String title, String body, String icon) {
        Notification saved = notifRepo.save(Notification.builder()
                .recipientRole(role)
                .branchId(branchId)
                .type(type)
                .title(title)
                .body(body)
                .icon(icon)
                .build());

        // Emitir a SSE en tiempo real
        realtimeEventService.broadcastEvent("NOTIFICATION_ADDED", saved, role, branchId);

        // Despachar Push Notification Web para clientes con app cerrada
        webPushService.sendPushToRole(role, branchId, title, body, icon, "/attendance");
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
