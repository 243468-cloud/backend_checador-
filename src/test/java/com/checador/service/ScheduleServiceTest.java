package com.checador.service;

import com.checador.entity.Branch;
import com.checador.entity.ScheduleRoster;
import com.checador.entity.ScheduleRoster.RosterStatus;
import com.checador.exception.ResourceNotFoundException;
import com.checador.repository.BranchRepository;
import com.checador.repository.ScheduleRosterRepository;
import com.checador.repository.UserRepository;
import com.checador.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleRosterRepository rosterRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ScheduleService scheduleService;

    private final Long tenantId = 1L;
    private final Long branchId = 10L;
    private final LocalDate weekStart = LocalDate.of(2026, 10, 19);
    private Branch branch;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
        branch = new Branch();
        branch.setId(branchId);
        branch.setName("Sucursal Central");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getRoster: con branch autorizado devuelve las celdas del roster")
    void test_getRoster_authorizedBranch() {
        when(branchRepository.findByIdAndTenantId(branchId, tenantId)).thenReturn(Optional.of(branch));
        ScheduleRoster cell = ScheduleRoster.builder()
                .branch(branch)
                .rowKey("row-1")
                .employeeName("Juan")
                .statusType(RosterStatus.NORMAL)
                .weekStart(weekStart)
                .build();
        when(rosterRepository.findByBranchIdAndWeekStart(branchId, weekStart)).thenReturn(List.of(cell));

        List<ScheduleRoster> result = scheduleService.getRoster(branchId, weekStart);

        assertEquals(1, result.size());
        assertEquals("Juan", result.get(0).getEmployeeName());
        verify(branchRepository).findByIdAndTenantId(branchId, tenantId);
        verify(rosterRepository).findByBranchIdAndWeekStart(branchId, weekStart);
    }

    @Test
    @DisplayName("getRoster: con branch ajeno o no existente lanza ResourceNotFoundException")
    void test_getRoster_crossTenantBranch() {
        when(branchRepository.findByIdAndTenantId(branchId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> scheduleService.getRoster(branchId, weekStart));
        verify(rosterRepository, never()).findByBranchIdAndWeekStart(anyLong(), any());
    }

    @Test
    @DisplayName("getAvailableWeeks: con branch autorizado devuelve lista de semanas")
    void test_getAvailableWeeks_authorizedBranch() {
        when(branchRepository.findByIdAndTenantId(branchId, tenantId)).thenReturn(Optional.of(branch));
        when(rosterRepository.findDistinctWeekStartsByBranchId(branchId)).thenReturn(List.of(weekStart));

        List<LocalDate> result = scheduleService.getAvailableWeeks(branchId);

        assertEquals(1, result.size());
        assertEquals(weekStart, result.get(0));
        verify(branchRepository).findByIdAndTenantId(branchId, tenantId);
        verify(rosterRepository).findDistinctWeekStartsByBranchId(branchId);
    }

    @Test
    @DisplayName("getAvailableWeeks: con branch ajeno o no existente lanza ResourceNotFoundException")
    void test_getAvailableWeeks_crossTenantBranch() {
        when(branchRepository.findByIdAndTenantId(branchId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> scheduleService.getAvailableWeeks(branchId));
        verify(rosterRepository, never()).findDistinctWeekStartsByBranchId(anyLong());
    }

    @Test
    @DisplayName("saveRoster: con branch autorizado borra y guarda celdas de forma atómica")
    void test_saveRoster_authorizedBranch() {
        when(branchRepository.findByIdAndTenantId(branchId, tenantId)).thenReturn(Optional.of(branch));
        when(rosterRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> cellData = Map.of(
                "rowKey", "row-1",
                "areaName", "COCINA",
                "dayIndex", 0,
                "employeeName", "Carlos",
                "statusType", "NORMAL"
        );

        List<ScheduleRoster> result = scheduleService.saveRoster(branchId, weekStart, List.of(cellData));

        assertEquals(1, result.size());
        assertEquals("Carlos", result.get(0).getEmployeeName());
        verify(rosterRepository).deleteByBranchIdAndWeekStart(branchId, weekStart);
        verify(rosterRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("saveRoster: con branch ajeno o no existente lanza ResourceNotFoundException")
    void test_saveRoster_crossTenantBranch() {
        when(branchRepository.findByIdAndTenantId(branchId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> scheduleService.saveRoster(branchId, weekStart, List.of()));
        verify(rosterRepository, never()).deleteByBranchIdAndWeekStart(anyLong(), any());
        verify(rosterRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("deleteRoster: con branch autorizado elimina celdas de la semana")
    void test_deleteRoster_authorizedBranch() {
        when(branchRepository.findByIdAndTenantId(branchId, tenantId)).thenReturn(Optional.of(branch));

        scheduleService.deleteRoster(branchId, weekStart);

        verify(branchRepository).findByIdAndTenantId(branchId, tenantId);
        verify(rosterRepository).deleteByBranchIdAndWeekStart(branchId, weekStart);
    }

    @Test
    @DisplayName("deleteRoster: con branch ajeno o no existente lanza ResourceNotFoundException")
    void test_deleteRoster_crossTenantBranch() {
        when(branchRepository.findByIdAndTenantId(branchId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> scheduleService.deleteRoster(branchId, weekStart));
        verify(rosterRepository, never()).deleteByBranchIdAndWeekStart(anyLong(), any());
    }

    @Test
    @DisplayName("calculateScheduleSummary: con branch autorizado calcula balance y horas")
    void test_calculateScheduleSummary_authorizedBranch() {
        when(branchRepository.findByIdAndTenantId(branchId, tenantId)).thenReturn(Optional.of(branch));
        ScheduleRoster cell = ScheduleRoster.builder()
                .branch(branch)
                .rowKey("r1")
                .employeeName("Ana")
                .dayIndex(0)
                .areaName("BARRA")
                .statusType(RosterStatus.NORMAL)
                .shiftStartTime("08:00")
                .shiftEndTime("16:00")
                .build();
        when(rosterRepository.findByBranchIdAndWeekStart(branchId, weekStart)).thenReturn(List.of(cell));

        ScheduleService.ScheduleSummaryDTO summary = scheduleService.calculateScheduleSummary(branchId, weekStart);

        assertNotNull(summary);
        assertEquals(1, summary.totalEmployees());
        assertEquals(8, summary.avgHours());
        assertEquals(1, summary.employeeBalances().size());
        assertEquals("Ana", summary.employeeBalances().get(0).name());
    }

    @Test
    @DisplayName("calculateScheduleSummary: con branch ajeno o no existente lanza ResourceNotFoundException")
    void test_calculateScheduleSummary_crossTenantBranch() {
        when(branchRepository.findByIdAndTenantId(branchId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> scheduleService.calculateScheduleSummary(branchId, weekStart));
        verify(rosterRepository, never()).findByBranchIdAndWeekStart(anyLong(), any());
    }

    @Test
    @DisplayName("getIndividualSchedules: con branch autorizado procesa días de trabajo")
    void test_getIndividualSchedules_authorizedBranch() {
        when(branchRepository.findByIdAndTenantId(branchId, tenantId)).thenReturn(Optional.of(branch));
        ScheduleRoster cell = ScheduleRoster.builder()
                .branch(branch)
                .rowKey("r1")
                .employeeName("Sofia")
                .dayIndex(1)
                .areaName("SALA")
                .statusType(RosterStatus.NORMAL)
                .shiftStartTime("09:00")
                .shiftEndTime("17:00")
                .build();
        when(rosterRepository.findByBranchIdAndWeekStart(branchId, weekStart)).thenReturn(List.of(cell));

        List<ScheduleService.EmployeeIndividualScheduleDTO> individual = scheduleService.getIndividualSchedules(branchId, weekStart);

        assertNotNull(individual);
        assertEquals(1, individual.size());
        assertEquals("Sofia", individual.get(0).employeeName());
        assertEquals(1, individual.get(0).days().size());
        assertEquals("Martes", individual.get(0).days().get(0).dayName());
    }

    @Test
    @DisplayName("getIndividualSchedules: con branch ajeno o no existente lanza ResourceNotFoundException")
    void test_getIndividualSchedules_crossTenantBranch() {
        when(branchRepository.findByIdAndTenantId(branchId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> scheduleService.getIndividualSchedules(branchId, weekStart));
        verify(rosterRepository, never()).findByBranchIdAndWeekStart(anyLong(), any());
    }
}
