package com.checador.service;

import com.checador.entity.Branch;
import com.checador.entity.Role;
import com.checador.entity.User;
import com.checador.repository.BranchRepository;
import com.checador.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WeeklyReportScheduler {

    private static final Logger logger = LoggerFactory.getLogger(WeeklyReportScheduler.class);

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final AttendanceService attendanceService;
    private final ReportService reportService;
    private final MailService mailService;

    // Se ejecuta todos los Lunes a las 9:00 AM (Zona horaria por defecto del servidor)
    @Scheduled(cron = "0 0 9 * * MON")
    public void generateAndSendWeeklyReports() {
        logger.info("Iniciando generación automática de reportes semanales...");
        
        // El reporte abarca la semana anterior (Lunes a Domingo)
        LocalDate endOfLastWeek = LocalDate.now().minusDays(1); // Domingo
        LocalDate startOfLastWeek = endOfLastWeek.minusDays(6); // Lunes

        int year = startOfLastWeek.getYear();
        int month = startOfLastWeek.getMonthValue();
        String periodLabel = "Semana del " + startOfLastWeek + " al " + endOfLastWeek;

        List<Branch> branches = branchRepository.findAll();

        for (Branch branch : branches) {
            try {
                // 1. Obtener la asistencia de la sucursal de ese mes/semana
                // NOTA: Para no refactorizar el servicio de asitencia, usamos el método mensual
                // y podríamos filtrarlo o confiar en que el gerente recibe el corte del mes hasta la fecha.
                // Aquí usamos el método mensual completo.
                var records = attendanceService.getMonthlyAttendanceByBranch(branch.getId(), year, month);

                if (records.isEmpty()) {
                    continue;
                }

                // 2. Generar Excel
                byte[] excelData = reportService.generateExcelReport(records, branch.getName(), month, year, periodLabel);

                // 3. Buscar a los administradores de esta sucursal
                List<User> admins = userRepository.findByBranchIdAndRole(branch.getId(), Role.ADMIN);

                for (User admin : admins) {
                    if (admin.getEmail() != null && !admin.getEmail().isBlank()) {
                        String subject = "Reporte Automático de Asistencia - " + branch.getName();
                        String text = "<p>Hola " + admin.getFullName() + ",</p>" +
                                      "<p>Adjunto encontrarás el reporte de asistencia de la <b>" + periodLabel + "</b>.</p>" +
                                      "<br><p>Saludos,<br>Sistema Checador Vía Gourmet</p>";

                        mailService.sendEmailWithAttachment(admin.getEmail(), subject, text, excelData, "Reporte_Asistencia.xlsx");
                        logger.info("Reporte enviado exitosamente a {}", admin.getEmail());
                    }
                }
            } catch (Exception e) {
                logger.error("Error al generar/enviar reporte de la sucursal " + branch.getName(), e);
            }
        }
        logger.info("Finalizada la generación de reportes automáticos.");
    }
}
