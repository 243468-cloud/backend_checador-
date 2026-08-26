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
        private int lateMinutes;
        private int onTimeCount;
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
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate startOfMonth = currentMonth.atDay(1);
        LocalDate endOfMonth = currentMonth.atEndOfMonth();

        // 1. Fetch active employees
        List<User> employees = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.EMPLOYEE && Boolean.TRUE.equals(u.getActive()))
                .collect(Collectors.toList());

        // 2. Fetch monthly attendance records
        List<Attendance> monthlyAttendances = attendanceRepository.findByDateBetween(startOfMonth, endOfMonth);

        Map<Long, Integer> attendanceCountMap = new HashMap<>();
        Map<Long, Integer> lateMinutesMap = new HashMap<>();
        Map<Long, Integer> onTimeMap = new HashMap<>();

        for (Attendance att : monthlyAttendances) {
            Long empId = att.getEmployee().getId();
            if (att.getStatus() != AttendanceStatus.ABSENT && att.getCheckIn() != null) {
                attendanceCountMap.put(empId, attendanceCountMap.getOrDefault(empId, 0) + 1);
            }
            if (att.getStatus() == AttendanceStatus.ON_TIME) {
                onTimeMap.put(empId, onTimeMap.getOrDefault(empId, 0) + 1);
            }
            if (att.getLateMinutes() != null && att.getLateMinutes() > 0) {
                lateMinutesMap.put(empId, lateMinutesMap.getOrDefault(empId, 0) + att.getLateMinutes());
            }
        }

        // 3. Build employee rank DTOs
        List<EmployeeRankDTO> allRankings = employees.stream().map(emp -> {
            Long id = emp.getId();
            return EmployeeRankDTO.builder()
                    .id(id)
                    .name(emp.getFullName() != null ? emp.getFullName() : emp.getUsername())
                    .username(emp.getUsername())
                    .branch(emp.getBranch() != null ? emp.getBranch().getName() : "Vía Gourmet")
                    .shift(emp.getShiftType() != null ? emp.getShiftType().name() : "MATUTINO")
                    .attendances(attendanceCountMap.getOrDefault(id, 0))
                    .lateMinutes(lateMinutesMap.getOrDefault(id, 0))
                    .onTimeCount(onTimeMap.getOrDefault(id, 0))
                    .build();
        }).collect(Collectors.toList());

        // 4. Sort Fortnightly (Most attendances, then on-time count)
        List<EmployeeRankDTO> fortnightRank = allRankings.stream()
                .sorted(Comparator.comparingInt(EmployeeRankDTO::getAttendances).reversed()
                        .thenComparingInt(EmployeeRankDTO::getOnTimeCount).reversed())
                .limit(3)
                .collect(Collectors.toList());

        // 5. Sort Monthly (Lowest late minutes, then attendances)
        List<EmployeeRankDTO> monthlyRank = allRankings.stream()
                .sorted(Comparator.comparingInt(EmployeeRankDTO::getLateMinutes)
                        .thenComparing(Comparator.comparingInt(EmployeeRankDTO::getAttendances).reversed()))
                .limit(3)
                .collect(Collectors.toList());

        return RankingResponseDTO.builder()
                .fortnightRank(fortnightRank)
                .monthlyRank(monthlyRank)
                .config(getConfig())
                .build();
    }
}
