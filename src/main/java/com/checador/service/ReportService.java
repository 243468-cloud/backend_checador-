package com.checador.service;

import com.checador.entity.Attendance;
import com.checador.entity.AttendanceStatus;
import com.checador.entity.User;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Genera un archivo Excel con el reporte mensual de asistencia.
     */
    public byte[] generateExcelReport(List<Attendance> records, String branchName, int month, int year) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            // 1. Hoja de Resumen (primera)
            addSummarySheet(wb, records, branchName, month, year);

            // 2. Hoja de Asistencia General
            Sheet sheet = wb.createSheet("Asistencia General");

            // Estilos
            CellStyle titleStyle = createTitleStyle(wb);
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle onTimeStyle = createStatusStyle(wb, IndexedColors.LIGHT_GREEN);
            CellStyle lateStyle = createStatusStyle(wb, IndexedColors.GOLD);
            CellStyle absentStyle = createStatusStyle(wb, IndexedColors.ROSE);
            CellStyle dataStyle = wb.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.CENTER);

            // Título
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Reporte de Asistencia — " + branchName);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

            // Período
            Row periodRow = sheet.createRow(1);
            Cell periodCell = periodRow.createCell(0);
            periodCell.setCellValue("Período: " + month + "/" + year);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 7));

            // Encabezados
            Row headerRow = sheet.createRow(3);
            String[] headers = {"Empleado", "Fecha", "Turno", "Entrada", "Salida", "Estado", "Tardanza (min)", "Horas trabajadas"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            // Datos
            int rowNum = 4;
            for (Attendance a : records) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(a.getUser().getFullName());
                row.createCell(1).setCellValue(a.getAttendanceDate().format(DATE_FMT));
                row.createCell(2).setCellValue(translateShift(a.getShiftType()));

                Cell entradaCell = row.createCell(3);
                entradaCell.setCellValue(a.getCheckInTime() != null ? a.getCheckInTime().format(TIME_FMT) : "—");

                Cell salidaCell = row.createCell(4);
                salidaCell.setCellValue(a.getCheckOutTime() != null ? a.getCheckOutTime().format(TIME_FMT) : "—");

                Cell statusCell = row.createCell(5);
                statusCell.setCellValue(translateStatus(a.getStatus()));
                statusCell.setCellStyle(getStatusStyle(a.getStatus(), onTimeStyle, lateStyle, absentStyle));

                row.createCell(6).setCellValue(a.getLateMinutes() != null ? a.getLateMinutes() : 0);

                Cell hoursCell = row.createCell(7);
                double actualH = a.getActualHoursWorked();
                hoursCell.setCellValue(actualH > 0 ? String.format("%.1f h", actualH) : "—");
            }

            // 3. Hojas de detalle individuales por empleado
            java.util.Map<Long, User> empMap = new java.util.LinkedHashMap<>();
            java.util.Map<Long, List<Attendance>> byEmp = new java.util.LinkedHashMap<>();
            for (Attendance a : records) {
                if (a.getUser() == null) continue;
                long uid = a.getUser().getId();
                empMap.putIfAbsent(uid, a.getUser());
                byEmp.computeIfAbsent(uid, k -> new java.util.ArrayList<>()).add(a);
            }

            for (java.util.Map.Entry<Long, List<Attendance>> entry : byEmp.entrySet()) {
                User emp = empMap.get(entry.getKey());
                String fullName = (emp != null && emp.getFullName() != null && !emp.getFullName().isBlank())
                                        ? emp.getFullName() : "Empleado " + entry.getKey();
                List<Attendance> list = entry.getValue();

                String safeBase = org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(fullName);
                String safeSheetName = safeBase;
                int sheetCounter = 1;
                while (wb.getSheet(safeSheetName) != null) {
                    String suffix = " (" + sheetCounter + ")";
                    int maxLen = 31 - suffix.length();
                    String truncated = safeBase.length() > maxLen ? safeBase.substring(0, maxLen) : safeBase;
                    safeSheetName = org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(truncated + suffix);
                    sheetCounter++;
                }
                Sheet detail = wb.createSheet(safeSheetName);

                // Título
                Row dt = detail.createRow(0);
                Cell dtc = dt.createCell(0);
                dtc.setCellValue("Asistencia — " + fullName);
                dtc.setCellStyle(titleStyle);
                detail.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

                // Encabezados
                Row dh = detail.createRow(2);
                String[] dCols = {"Fecha", "Turno", "Entrada", "Salida", "Estado", "Tardanza (min)", "Horas trabajadas"};
                for (int i = 0; i < dCols.length; i++) {
                    Cell c = dh.createCell(i);
                    c.setCellValue(dCols[i]);
                    c.setCellStyle(headerStyle);
                    detail.setColumnWidth(i, 4800);
                }

                // Datos
                int dr = 3;
                for (Attendance a : list.stream().sorted(
                        java.util.Comparator.comparing(Attendance::getAttendanceDate)).toList()) {
                    Row row = detail.createRow(dr++);
                    row.createCell(0).setCellValue(a.getAttendanceDate().format(DATE_FMT));
                    row.createCell(1).setCellValue(translateShift(a.getShiftType()));
                    row.createCell(2).setCellValue(a.getCheckInTime() != null ? a.getCheckInTime().format(TIME_FMT) : "—");
                    row.createCell(3).setCellValue(a.getCheckOutTime() != null ? a.getCheckOutTime().format(TIME_FMT) : "—");

                    Cell sc = row.createCell(4);
                    sc.setCellValue(translateStatus(a.getStatus()));
                    sc.setCellStyle(getStatusStyle(a.getStatus(), onTimeStyle, lateStyle, absentStyle));

                    row.createCell(5).setCellValue(a.getLateMinutes() != null ? a.getLateMinutes() : 0);

                    Cell hoursCell = row.createCell(6);
                    double actualH = a.getActualHoursWorked();
                    hoursCell.setCellValue(actualH > 0 ? String.format("%.1f h", actualH) : "—");
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void addSummarySheet(XSSFWorkbook wb, List<Attendance> records, String branchName, int month, int year) {
        Sheet summary = wb.createSheet("Resumen");

        long onTime = records.stream().filter(a -> a.getStatus() == AttendanceStatus.ON_TIME).count();
        long late = records.stream().filter(a -> a.getStatus() == AttendanceStatus.LATE).count();
        long absent = records.stream().filter(a -> a.getStatus() == AttendanceStatus.ABSENT).count();
        long total = records.size();
        double punctuality = total > 0 ? (double) onTime / total * 100 : 0;

        String[][] data = {
            {"Sucursal", branchName},
            {"Período", month + "/" + year},
            {"Total registros", String.valueOf(total)},
            {"Puntual", String.valueOf(onTime)},
            {"Tardanza", String.valueOf(late)},
            {"Falta", String.valueOf(absent)},
            {"% Puntualidad", String.format("%.1f%%", punctuality)}
        };

        for (int i = 0; i < data.length; i++) {
            Row row = summary.createRow(i);
            row.createCell(0).setCellValue(data[i][0]);
            row.createCell(1).setCellValue(data[i][1]);
        }
        summary.setColumnWidth(0, 5000);
        summary.setColumnWidth(1, 4000);
    }

    /**
     * Genera el reporte de Pre-Nómina (Incidencias) agrupado por empleado.
     * Columnas: Horas Ordinarias, Horas Extra, Min. Retardo, Faltas Injustificadas, Faltas Justificadas.
     */
    public byte[] generatePayrollReport(List<Attendance> records, String branchName, int month, int year) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            CellStyle titleStyle  = createTitleStyle(wb);
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle numStyle    = wb.createCellStyle();
            numStyle.setAlignment(HorizontalAlignment.CENTER);
            CellStyle warnStyle   = createStatusStyle(wb, IndexedColors.ROSE);
            warnStyle.setAlignment(HorizontalAlignment.CENTER);
            CellStyle okStyle     = createStatusStyle(wb, IndexedColors.LIGHT_GREEN);
            okStyle.setAlignment(HorizontalAlignment.CENTER);

            // ── Hoja por empleado (detalle) ───────────────────────────────
            Map<Long, User>             empMap    = new java.util.LinkedHashMap<>();
            Map<Long, List<Attendance>> byEmp     = new java.util.LinkedHashMap<>();

            for (Attendance a : records) {
                if (a.getUser() == null) continue;
                long uid = a.getUser().getId();
                empMap.putIfAbsent(uid, a.getUser());
                byEmp.computeIfAbsent(uid, k -> new java.util.ArrayList<>()).add(a);
            }

            // ── Hoja Global de Pre-Nómina ─────────────────────────────────
            Sheet global = wb.createSheet("Pre-Nómina");

            // Título
            Row t0 = global.createRow(0);
            Cell tc = t0.createCell(0);
            tc.setCellValue("Pre-Nómina — " + branchName + " — " + month + "/" + year);
            tc.setCellStyle(titleStyle);
            global.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

            // Encabezados globales
            String[] cols = {
                "Empleado", "Días Trabajados",
                "Hrs Ordinarias", "Hrs Extra",
                "Min. Retardo Acum.", "Faltas Injustificadas", "Faltas Justificadas",
                "Observaciones"
            };
            Row gh = global.createRow(2);
            for (int i = 0; i < cols.length; i++) {
                Cell c = gh.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
                global.setColumnWidth(i, 5500);
            }

            int globalRow = 3;
            for (Map.Entry<Long, List<Attendance>> entry : byEmp.entrySet()) {
                User emp              = empMap.get(entry.getKey());
                String fullName       = (emp != null && emp.getFullName() != null && !emp.getFullName().isBlank())
                                        ? emp.getFullName() : "Empleado " + entry.getKey();
                List<Attendance> list = entry.getValue();

                double ordinaryHours = 0;
                double extraHours    = 0;
                int tardyMinutes     = 0;
                int absUnj           = 0;
                int absJus           = 0;
                int worked           = 0;

                for (Attendance a : list) {
                    switch (a.getStatus()) {
                        case ON_TIME, LATE, IN_SHIFT -> {
                            // Cambio 3: registros sin checkout están incompletos — no los sumamos al total
                            if (a.getCheckOutTime() == null) break;
                            worked++;
                            double h = a.getActualHoursWorked();
                            double shiftHours = getShiftHours(a.getShiftType());

                            double ordinary = Math.min(h, shiftHours);
                            double extra    = a.getEffectiveExtraHours(shiftHours); // misma fórmula que siempre

                            ordinaryHours += ordinary;
                            extraHours    += extra;
                            tardyMinutes  += a.getLateMinutes() != null ? a.getLateMinutes() : 0;
                        }
                        case ABSENT   -> absUnj++;
                        case EXCUSED  -> absJus++;
                    }
                }

                // Cambio 2: contar turnos abiertos para incluirlos en Observaciones
                long openShifts = list.stream()
                        .filter(a -> a.getCheckOutTime() == null
                                && a.getStatus() != AttendanceStatus.ABSENT
                                && a.getStatus() != AttendanceStatus.EXCUSED)
                        .count();

                Row gr = global.createRow(globalRow++);
                gr.createCell(0).setCellValue(fullName);
                gr.createCell(1).setCellValue(worked);

                Cell ordC = gr.createCell(2);
                ordC.setCellValue(Math.round(ordinaryHours * 10.0) / 10.0);
                ordC.setCellStyle(numStyle);

                Cell extC = gr.createCell(3);
                extC.setCellValue(Math.round(extraHours * 10.0) / 10.0);
                extC.setCellStyle(extraHours > 0 ? okStyle : numStyle);

                Cell tardC = gr.createCell(4);
                tardC.setCellValue(tardyMinutes);
                tardC.setCellStyle(tardyMinutes > 30 ? warnStyle : numStyle);

                Cell absUnjC = gr.createCell(5);
                absUnjC.setCellValue(absUnj);
                absUnjC.setCellStyle(absUnj > 0 ? warnStyle : numStyle);

                Cell absJusC = gr.createCell(6);
                absJusC.setCellValue(absJus);
                absJusC.setCellStyle(numStyle);

                // Observaciones automáticas
                StringBuilder obs = new StringBuilder();
                if (absUnj > 2) obs.append("⚠ Múltiples faltas. ");
                if (tardyMinutes > 60) obs.append("⚠ Retardo acumulado >1h. ");
                if (extraHours > 5) obs.append("★ Horas extra significativas. ");
                // Cambio 2: advertir sobre turnos sin salida
                if (openShifts > 0) obs.append("⚠ ").append(openShifts).append(" registro(s) sin salida — totales incompletos. ");
                gr.createCell(7).setCellValue(obs.toString().trim());

                // ── Hoja de detalle por empleado (nombre seguro e único) ─────────
                String safeBase = org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(fullName);
                String safeSheetName = safeBase;
                int sheetCounter = 1;
                while (wb.getSheet(safeSheetName) != null) {
                    String suffix = " (" + sheetCounter + ")";
                    int maxLen = 31 - suffix.length();
                    String truncated = safeBase.length() > maxLen ? safeBase.substring(0, maxLen) : safeBase;
                    safeSheetName = org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(truncated + suffix);
                    sheetCounter++;
                }
                Sheet detail = wb.createSheet(safeSheetName);

                Row dt = detail.createRow(0);
                Cell dtc = dt.createCell(0);
                dtc.setCellValue("Detalle — " + fullName);
                dtc.setCellStyle(titleStyle);
                detail.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

                Row dh = detail.createRow(2);
                String[] dCols = {"Fecha", "Entrada", "Salida", "Estado", "Hrs Ordinarias", "Hrs Extra", "Min. Retardo"};
                for (int i = 0; i < dCols.length; i++) {
                    Cell c = dh.createCell(i);
                    c.setCellValue(dCols[i]);
                    c.setCellStyle(headerStyle);
                    detail.setColumnWidth(i, 4800);
                }

                int dr = 3;
                for (Attendance a : list.stream().sorted(
                        java.util.Comparator.comparing(Attendance::getAttendanceDate)).toList()) {

                    boolean missingCheckout = a.getCheckOutTime() == null
                            && a.getStatus() != AttendanceStatus.ABSENT
                            && a.getStatus() != AttendanceStatus.EXCUSED;

                    Row row = detail.createRow(dr++);
                    row.createCell(0).setCellValue(a.getAttendanceDate().format(DATE_FMT));
                    row.createCell(1).setCellValue(a.getCheckInTime() != null ? a.getCheckInTime().format(TIME_FMT) : "—");

                    // Cambio 2: marcar visualmente cuando falta la salida
                    Cell checkOutCell = row.createCell(2);
                    if (missingCheckout) {
                        checkOutCell.setCellValue("⚠ Sin salida");
                        checkOutCell.setCellStyle(warnStyle);
                    } else {
                        checkOutCell.setCellValue(a.getCheckOutTime() != null ? a.getCheckOutTime().format(TIME_FMT) : "—");
                    }

                    Cell sc = row.createCell(3);
                    sc.setCellValue(translateStatus(a.getStatus()));
                    sc.setCellStyle(getStatusStyle(a.getStatus(),
                            createStatusStyle(wb, IndexedColors.LIGHT_GREEN),
                            createStatusStyle(wb, IndexedColors.GOLD),
                            createStatusStyle(wb, IndexedColors.ROSE)));

                    if (missingCheckout) {
                        // Cambio 3: registro abierto — mostrar guión en lugar de 0 para no confundir
                        row.createCell(4).setCellValue("—");
                        row.createCell(5).setCellValue("—");
                    } else {
                        double h          = a.getActualHoursWorked();
                        double shiftHours = getShiftHours(a.getShiftType());
                        double ordinary   = Math.min(h, shiftHours);
                        // Cambio 1: misma fórmula que el resumen — getEffectiveExtraHours() en vez de calc raw
                        double extra      = a.getEffectiveExtraHours(shiftHours);

                        row.createCell(4).setCellValue(Math.round(ordinary * 10.0) / 10.0);
                        row.createCell(5).setCellValue(Math.round(extra * 10.0) / 10.0);
                    }
                    row.createCell(6).setCellValue(a.getLateMinutes() != null ? a.getLateMinutes() : 0);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Retorna las horas nominales del turno para calcular horas extra. */
    private double getShiftHours(com.checador.entity.ShiftType shift) {
        if (shift == null) return 8.0;
        return switch (shift) {
            case MORNING -> 8.0;
            case EVENING -> 8.0;
            case SUNDAY  -> 10.0;
            case MIXED   -> 8.0;
        };
    }

    // ─── Helpers de estilo ────────────────────────────────────────────────────

    private CellStyle createTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createStatusStyle(Workbook wb, IndexedColors color) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle getStatusStyle(AttendanceStatus status, CellStyle onTime, CellStyle late, CellStyle absent) {
        return switch (status) {
            case ON_TIME -> onTime;
            case LATE    -> late;
            case ABSENT  -> absent;
            default      -> onTime;
        };
    }

    private String translateShift(com.checador.entity.ShiftType shift) {
        return switch (shift) {
            case MORNING -> "Matutino (7-15)";
            case EVENING -> "Vespertino (15-23)";
            case SUNDAY  -> "Dominical (8-18)";
            case MIXED   -> "Mixto (11-19)";
        };
    }

    private String translateStatus(AttendanceStatus status) {
        return switch (status) {
            case ON_TIME  -> "Puntual";
            case LATE     -> "Tardanza";
            case ABSENT   -> "Falta";
            case IN_SHIFT -> "En turno";
            case EXCUSED  -> "Justificado";
        };
    }
}
