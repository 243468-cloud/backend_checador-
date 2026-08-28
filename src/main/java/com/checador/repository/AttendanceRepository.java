package com.checador.repository;

import com.checador.entity.Attendance;
import com.checador.entity.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    @Query("SELECT a FROM Attendance a LEFT JOIN FETCH a.user LEFT JOIN FETCH a.branch WHERE a.user.id = :userId AND a.attendanceDate = :date")
    Optional<Attendance> findByUserIdAndAttendanceDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("SELECT a FROM Attendance a LEFT JOIN FETCH a.user LEFT JOIN FETCH a.branch WHERE a.user.id = :userId AND a.checkOutTime IS NULL ORDER BY a.checkInTime DESC")
    List<Attendance> findActiveAttendanceList(@Param("userId") Long userId);

    default Optional<Attendance> findTopByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(Long userId) {
        List<Attendance> list = findActiveAttendanceList(userId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    List<Attendance> findByUserIdAndAttendanceDateBetween(Long userId, LocalDate from, LocalDate to);

    List<Attendance> findByBranchIdAndAttendanceDate(Long branchId, LocalDate date);

    List<Attendance> findByBranchIdAndAttendanceDateBetween(Long branchId, LocalDate from, LocalDate to);

    @Query("SELECT a FROM Attendance a LEFT JOIN FETCH a.user LEFT JOIN FETCH a.branch WHERE (:branchId IS NULL OR a.branch.id = :branchId) AND a.attendanceDate = :date ORDER BY a.user.fullName")
    List<Attendance> findDailyAttendanceByBranch(@Param("branchId") Long branchId, @Param("date") LocalDate date);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE (:branchId IS NULL OR a.branch.id = :branchId) AND a.attendanceDate = :date AND (a.status = com.checador.entity.AttendanceStatus.ON_TIME OR a.status = com.checador.entity.AttendanceStatus.IN_SHIFT)")
    long countOnTimeByBranchAndDate(@Param("branchId") Long branchId, @Param("date") LocalDate date);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE (:branchId IS NULL OR a.branch.id = :branchId) AND a.attendanceDate = :date AND a.status = com.checador.entity.AttendanceStatus.LATE")
    long countLateByBranchAndDate(@Param("branchId") Long branchId, @Param("date") LocalDate date);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE (:branchId IS NULL OR a.branch.id = :branchId) AND a.attendanceDate = :date AND a.status = com.checador.entity.AttendanceStatus.ABSENT")
    long countAbsentByBranchAndDate(@Param("branchId") Long branchId, @Param("date") LocalDate date);

    @Query("SELECT a FROM Attendance a LEFT JOIN FETCH a.user LEFT JOIN FETCH a.branch WHERE a.user.id = :userId AND YEAR(a.attendanceDate) = :year AND MONTH(a.attendanceDate) = :month ORDER BY a.attendanceDate")
    List<Attendance> findMonthlyAttendanceByUser(@Param("userId") Long userId, @Param("year") int year, @Param("month") int month);

    @Query("SELECT a FROM Attendance a LEFT JOIN FETCH a.user LEFT JOIN FETCH a.branch WHERE (:branchId IS NULL OR a.branch.id = :branchId) AND YEAR(a.attendanceDate) = :year AND MONTH(a.attendanceDate) = :month ORDER BY a.attendanceDate, a.user.fullName")
    List<Attendance> findMonthlyAttendanceByBranch(@Param("branchId") Long branchId, @Param("year") int year, @Param("month") int month);

    boolean existsByUserIdAndAttendanceDate(Long userId, LocalDate date);

    @Modifying
    @Query("DELETE FROM Attendance a WHERE a.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
