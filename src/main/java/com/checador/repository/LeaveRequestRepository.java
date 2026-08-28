package com.checador.repository;

import com.checador.entity.LeaveRequest;
import com.checador.entity.LeaveRequest.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /** Solicitudes de un empleado específico. */
    List<LeaveRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Solicitudes pendientes de una sucursal (para admin). */
    List<LeaveRequest> findByBranchIdAndStatusOrderByCreatedAtDesc(Long branchId, LeaveStatus status);

    /** Todas las solicitudes de una sucursal (para admin). */
    List<LeaveRequest> findByBranchIdOrderByCreatedAtDesc(Long branchId);

    @Modifying
    @Query("DELETE FROM LeaveRequest l WHERE l.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
