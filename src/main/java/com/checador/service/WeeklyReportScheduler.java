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

    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // Se ejecuta externamente mediante el endpoint /api/reports/public/global/trigger-weekly
    @org.springframework.scheduling.annotation.Async
    public void generateAndSendWeeklyReportsAsync() {
        generateAndSendWeeklyReports();
    }

    public void generateAndSendWeeklyReports() {
        logger.info("Iniciando generación automática de reportes semanales...");
        
        LocalDate endOfLastWeek = LocalDate.now().minusDays(1); // Domingo
        LocalDate startOfLastWeek = endOfLastWeek.minusDays(6); // Lunes
        int year = startOfLastWeek.getYear();
        int month = startOfLastWeek.getMonthValue();
        String periodLabel = "Semana del " + startOfLastWeek + " al " + endOfLastWeek;

        // Obtener todos los tenants registrados usando JdbcTemplate para evadir el @TenantId global
        // Usamos la tabla 'users' para garantizar que se obtienen todos los tenants (incluidos los que no tengan settings)
        List<Long> tenantIds = jdbcTemplate.queryForList("SELECT DISTINCT tenant_id FROM users WHERE tenant_id > 0", Long.class);

        for (Long tenantId : tenantIds) {
            String companyName = "Checador";
            try {
                String fetched = jdbcTemplate.queryForObject(
                        "SELECT company_name FROM tenant_settings WHERE tenant_id = ?", String.class, tenantId);
                if (fetched != null && !fetched.isBlank()) {
                    companyName = fetched;
                }
            } catch (Exception ignored) {}

            try {
                com.checador.security.TenantContext.setTenantId(tenantId);
                List<Branch> branches = branchRepository.findAll();

                for (Branch branch : branches) {
                    try {
                        var records = attendanceService.getMonthlyAttendanceByBranch(branch.getId(), year, month);
                        if (records.isEmpty()) continue;

                        byte[] excelData = reportService.generateExcelReport(records, branch.getName(), month, year, periodLabel);
                        List<User> admins = userRepository.findByBranchIdAndRole(branch.getId(), Role.ADMIN);

                        for (User admin : admins) {
                            if (admin.getEmail() != null && !admin.getEmail().isBlank()) {
                                String subject = "Reporte Automático de Asistencia - " + branch.getName() + " (" + companyName + ")";
                                String text = "<p>Hola " + admin.getFullName() + ",</p>" +
                                              "<p>Adjunto encontrarás el reporte de asistencia de la <b>" + periodLabel + "</b>.</p>" +
                                              "<br><p>Saludos,<br>" + companyName + "</p>";

                                mailService.sendEmailWithAttachment(admin.getEmail(), subject, text, excelData, "Reporte_Asistencia.xlsx");
                                logger.info("Reporte enviado exitosamente a {} (Tenant {}, Empresa {})", admin.getEmail(), tenantId, companyName);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error al generar/enviar reporte de la sucursal " + branch.getName(), e);
                    }
                }
            } finally {
                com.checador.security.TenantContext.clear();
            }
        }
        logger.info("Finalizada la generación de reportes automáticos.");
    }
}
