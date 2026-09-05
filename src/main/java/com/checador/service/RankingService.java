package com.checador.service;

import com.checador.entity.Attendance;
import com.checador.entity.AttendanceStatus;
import com.checador.entity.Role;
import com.checador.entity.User;
import com.checador.repository.AttendanceRepository;
import com.checador.repository.UserRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    // Default reward configuration parameters
    private String fortnightReward = "Bebida sin alcohol (Smoothie / Mocktail Gourmet)";
    private String monthlyReward = "Platillo Especial Vía Gourmet a Elección";
    private int fortnightMinAttendance = 12;
    private int monthlyMaxLateMinutes = 0;

    @Data
    @Builder
    public static class RewardsConfigDTO {
        private String fortnightReward;
        private String monthlyReward;
        private int fortnightMinAttendance;
        private int monthlyMaxLateMinutes;
    }

    @Data
    @Builder
    public static class EmployeeRankDTO {
        private Long id;
        private String name;
        private String username;
        private String branch;
        private String shift;
        private int attendances;
        private int onTimeCount;
        private int lateCount;
        private int lateMinutes;
        private int absentCount;
        private double score; // 0.0 to 100.0 %
    }

    @Data
    @Builder
    public static class RankingResponseDTO {
        private List<EmployeeRankDTO> fortnightRank;
        private List<EmployeeRankDTO> monthlyRank;
        private RewardsConfigDTO config;
    }

    public RewardsConfigDTO getConfig() {
        return RewardsConfigDTO.builder()
                .fortnightReward(fortnightReward)
                .monthlyReward(monthlyReward)
                .fortnightMinAttendance(fortnightMinAttendance)
                .monthlyMaxLateMinutes(monthlyMaxLateMinutes)
                .build();
    }

    @Transactional
    public RewardsConfigDTO updateConfig(RewardsConfigDTO newConfig) {
        if (newConfig.getFortnightReward() != null && !newConfig.getFortnightReward().isBlank()) {
            this.fortnightReward = newConfig.getFortnightReward();
        }
        if (newConfig.getMonthlyReward() != null && !newConfig.getMonthlyReward().isBlank()) {
            this.monthlyReward = newConfig.getMonthlyReward();
        }
        if (newConfig.getFortnightMinAttendance() > 0) {
            this.fortnightMinAttendance = newConfig.getFortnightMinAttendance();
        }
        if (newConfig.getMonthlyMaxLateMinutes() >= 0) {
            this.monthlyMaxLateMinutes = newConfig.getMonthlyMaxLateMinutes();
        }
        return getConfig();
    }

    @Transactional(readOnly = true)
    public RankingResponseDTO calculateRanking() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Mexico_City"));
        YearMonth currentMonth = YearMonth.from(today);
        int year = currentMonth.getYear();
        int month = currentMonth.getMonthValue();
        boolean isFirstFortnight = today.getDayOfMonth() <= 15;

        // 1. Fetch active employees
        List<User> employees = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.EMPLOYEE && Boolean.TRUE.equals(u.getActive()))
                .collect(Collectors.toList());

        // 2. Fetch monthly attendance records from DB
        List<Attendance> monthlyAttendances = attendanceRepository.findMonthlyAttendanceByBranch(null, year, month);

        // Maps for Monthly Metrics
        Map<Long, Integer> mAttendancesMap = new HashMap<>();
        Map<Long, Integer> mOnTimeMap = new HashMap<>();
        Map<Long, Integer> mLateCountMap = new HashMap<>();
        Map<Long, Integer> mLateMinutesMap = new HashMap<>();
        Map<Long, Integer> mAbsentCountMap = new HashMap<>();

        // Maps for Fortnight Metrics (días 1-15 o 16-fin de mes)
        Map<Long, Integer> fnAttendancesMap = new HashMap<>();
        Map<Long, Integer> fnOnTimeMap = new HashMap<>();
        Map<Long, Integer> fnLateCountMap = new HashMap<>();
        Map<Long, Integer> fnLateMinutesMap = new HashMap<>();
        Map<Long, Integer> fnAbsentCountMap = new HashMap<>();

        for (Attendance att : monthlyAttendances) {
            if (att.getUser() == null || att.getAttendanceDate() == null) continue;
            Long empId = att.getUser().getId();
            boolean isFnRecord = isFirstFortnight
                    ? att.getAttendanceDate().getDayOfMonth() <= 15
                    : att.getAttendanceDate().getDayOfMonth() > 15;

            if (att.getStatus() == AttendanceStatus.ABSENT) {
                mAbsentCountMap.put(empId, mAbsentCountMap.getOrDefault(empId, 0) + 1);
                if (isFnRecord) {
                    fnAbsentCountMap.put(empId, fnAbsentCountMap.getOrDefault(empId, 0) + 1);
                }
            } else if (att.getCheckInTime() != null) {
                mAttendancesMap.put(empId, mAttendancesMap.getOrDefault(empId, 0) + 1);
                if (isFnRecord) {
                    fnAttendancesMap.put(empId, fnAttendancesMap.getOrDefault(empId, 0) + 1);
                }

                if (att.getStatus() == AttendanceStatus.ON_TIME) {
                    mOnTimeMap.put(empId, mOnTimeMap.getOrDefault(empId, 0) + 1);
                    if (isFnRecord) {
                        fnOnTimeMap.put(empId, fnOnTimeMap.getOrDefault(empId, 0) + 1);
                    }
                } else if (att.getStatus() == AttendanceStatus.LATE || (att.getLateMinutes() != null && att.getLateMinutes() > 0)) {
                    mLateCountMap.put(empId, mLateCountMap.getOrDefault(empId, 0) + 1);
                    int mins = att.getLateMinutes() != null ? att.getLateMinutes() : 0;
                    if (mins > 0) {
                        mLateMinutesMap.put(empId, mLateMinutesMap.getOrDefault(empId, 0) + mins);
                    }
                    if (isFnRecord) {
                        fnLateCountMap.put(empId, fnLateCountMap.getOrDefault(empId, 0) + 1);
                        if (mins > 0) {
                            fnLateMinutesMap.put(empId, fnLateMinutesMap.getOrDefault(empId, 0) + mins);
                        }
                    }
                }
            }
        }

        // Regla de score:
        //   100 puntos base
        //   - 5 pts por cada día con tardanza (lateCount)
        //   - hasta 10 pts por minutos acumulados de retardo (lateMins * 0.2, cap 10)
        //   - 15 pts por cada falta injustificada (absentCount)
        //   Quien llega impecable (0 tardanzas, 0 faltas) conserva 100.0 — siempre primero.
        List<EmployeeRankDTO> fortnightRank = employees.stream()
                .map(emp -> {
                    Long id = emp.getId();
                    int attendances = fnAttendancesMap.getOrDefault(id, 0);
                    int onTime = fnOnTimeMap.getOrDefault(id, 0);
                    int lates = fnLateCountMap.getOrDefault(id, 0);
                    int lateMins = fnLateMinutesMap.getOrDefault(id, 0);
                    int absents = fnAbsentCountMap.getOrDefault(id, 0);

                    double score = 0.0;
                    if (attendances > 0) {
                        // Cap lateMinutes penalty at 10 pts to avoid one long tardiness destroying an otherwise clean fortnight
                        double lateMinutesPenalty = Math.min(lateMins * 0.2, 10.0);
                        double penalty = (lates * 5.0) + lateMinutesPenalty + (absents * 15.0);
                        score = Math.max(0.0, Math.min(100.0, 100.0 - penalty));
                    }

                    return EmployeeRankDTO.builder()
                            .id(id)
                            .name(emp.getFullName() != null && !emp.getFullName().isBlank() ? emp.getFullName() : emp.getUsername())
                            .username(emp.getUsername())
                            .branch(emp.getBranch() != null ? emp.getBranch().getName() : "Vía Gourmet")
                            .shift(emp.getShiftType() != null ? emp.getShiftType().name() : "MATUTINO")
                            .attendances(attendances)
                            .onTimeCount(onTime)
                            .lateCount(lates)
                            .lateMinutes(lateMins)
                            .absentCount(absents)
                            .score(Math.round(score * 10.0) / 10.0)
                            .build();
                })
                .filter(e -> e.getAttendances() > 0) // Solo empleados con al menos 1 asistencia
                // Fix 1: ordenar por score primero (quien llegó impecable = 100.0 va arriba)
                // Fix 2: usar static Comparator.comparing() para evitar el bug de .reversed() encadenado en Java
                .sorted(Comparator
                        .comparingDouble((EmployeeRankDTO e) -> e.getScore())         // 1) mayor score arriba
                        .reversed()
                        .thenComparingInt((EmployeeRankDTO e) -> -e.getOnTimeCount()) // 2) más días puntual
                        .thenComparingInt((EmployeeRankDTO e) -> e.getLateCount())    // 3) menos tardanzas
                        .thenComparingInt((EmployeeRankDTO e) -> e.getLateMinutes())  // 4) menos minutos de retardo
                        .thenComparingInt((EmployeeRankDTO e) -> -e.getAttendances()) // 5) más días trabajados
                )
                .limit(3)
                .collect(Collectors.toList());

        List<EmployeeRankDTO> monthlyRank = employees.stream()
                .map(emp -> {
                    Long id = emp.getId();
                    int attendances = mAttendancesMap.getOrDefault(id, 0);
                    int onTime = mOnTimeMap.getOrDefault(id, 0);
                    int lates = mLateCountMap.getOrDefault(id, 0);
                    int lateMins = mLateMinutesMap.getOrDefault(id, 0);
                    int absents = mAbsentCountMap.getOrDefault(id, 0);

                    double score = 0.0;
                    if (attendances > 0) {
                        // Misma fórmula que la quincenal — cap en 10 pts para minutos de retardo
                        double lateMinutesPenalty = Math.min(lateMins * 0.2, 10.0);
                        double penalty = (lates * 5.0) + lateMinutesPenalty + (absents * 15.0);
                        score = Math.max(0.0, Math.min(100.0, 100.0 - penalty));
                    }

                    return EmployeeRankDTO.builder()
                            .id(id)
                            .name(emp.getFullName() != null && !emp.getFullName().isBlank() ? emp.getFullName() : emp.getUsername())
                            .username(emp.getUsername())
                            .branch(emp.getBranch() != null ? emp.getBranch().getName() : "Vía Gourmet")
                            .shift(emp.getShiftType() != null ? emp.getShiftType().name() : "MATUTINO")
                            .attendances(attendances)
                            .onTimeCount(onTime)
                            .lateCount(lates)
                            .lateMinutes(lateMins)
                            .absentCount(absents)
                            .score(Math.round(score * 10.0) / 10.0)
                            .build();
                })
                .filter(e -> e.getAttendances() > 0) // Solo empleados con al menos 1 asistencia
                // Fix 2: static Comparator.comparing() evita el bug de .reversed() encadenado en Java
                .sorted(Comparator
                        .comparingDouble((EmployeeRankDTO e) -> e.getScore())         // 1) mayor score arriba
                        .reversed()
                        .thenComparingInt((EmployeeRankDTO e) -> -e.getOnTimeCount()) // 2) más días puntual
                        .thenComparingInt((EmployeeRankDTO e) -> e.getLateCount())    // 3) menos tardanzas
                        .thenComparingInt((EmployeeRankDTO e) -> e.getLateMinutes())  // 4) menos minutos de retardo
                        .thenComparingInt((EmployeeRankDTO e) -> e.getAbsentCount())  // 5) menos faltas
                        .thenComparingInt((EmployeeRankDTO e) -> -e.getAttendances()) // 6) más días trabajados
                )
                .limit(3)
                .collect(Collectors.toList());

        return RankingResponseDTO.builder()
                .fortnightRank(fortnightRank)
                .monthlyRank(monthlyRank)
                .config(getConfig())
                .build();
    }
}
