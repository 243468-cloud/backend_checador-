package com.checador.repository;

import com.checador.entity.LeaveRequest;
import com.checador.entity.LeaveRequest.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /** Solicitudes de un empleado específico. */
    List<LeaveRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Solicitudes pendientes de una sucursal (para admin). */
    List<LeaveRequest> findByBranchIdAndStatusOrderByCreatedAtDesc(Long branchId, LeaveStatus status);

    /** Todas las solicitudes de una sucursal (para admin). */
    List<LeaveRequest> findByBranchIdOrderByCreatedAtDesc(Long branchId);
}
