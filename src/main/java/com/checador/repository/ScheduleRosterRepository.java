package com.checador.repository;

import com.checador.entity.ScheduleRoster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScheduleRosterRepository extends JpaRepository<ScheduleRoster, Long> {

    /** Obtiene toda la matriz de una sucursal para la semana indicada. */
    List<ScheduleRoster> findByBranchIdAndWeekStart(Long branchId, LocalDate weekStart);

    /** Elimina todas las celdas de una sucursal+semana (para reemplazar la matriz completa). */
    @Modifying
    @Transactional
    @Query("DELETE FROM ScheduleRoster r WHERE r.branch.id = :branchId AND r.weekStart = :weekStart")
    void deleteByBranchIdAndWeekStart(Long branchId, LocalDate weekStart);

    /** Semanas con registros para una sucursal (para el selector de semana). */
    @Query("SELECT DISTINCT r.weekStart FROM ScheduleRoster r WHERE r.branch.id = :branchId ORDER BY r.weekStart DESC")
    List<LocalDate> findDistinctWeekStartsByBranchId(Long branchId);

    /** Busca horarios/cambios de turno específicos de un empleado en un día determinado. */
    @Query("SELECT r FROM ScheduleRoster r WHERE r.branch.id = :branchId AND r.weekStart = :weekStart AND r.dayIndex = :dayIndex AND (UPPER(r.employeeName) LIKE UPPER(CONCAT('%', :namePattern, '%')))")
    List<ScheduleRoster> findRosterForEmployeeDay(Long branchId, LocalDate weekStart, Integer dayIndex, String namePattern);
}
