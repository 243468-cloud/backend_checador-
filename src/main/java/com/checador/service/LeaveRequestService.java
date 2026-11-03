package com.checador.service;

import com.checador.entity.Branch;
import com.checador.entity.LeaveRequest;
import com.checador.entity.LeaveRequest.LeaveStatus;
import com.checador.entity.LeaveRequest.LeaveType;
import com.checador.entity.User;
import com.checador.repository.BranchRepository;
import com.checador.repository.LeaveRequestRepository;
import org.springframework.transaction.annotation.Transactional;
import com.checador.security.TenantContext;
import org.springframework.stereotype.Service;
import com.checador.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRepository;
    private final BranchRepository branchRepository;

    public LeaveRequestService(LeaveRequestRepository leaveRepository, BranchRepository branchRepository) {
        this.leaveRepository = leaveRepository;
        this.branchRepository = branchRepository;
    }

    // ─── Empleado ──────────────────────────────────────────────────────────────

    /** Crea una solicitud de permiso/incapacidad/vacaciones. */
    @Transactional
    public LeaveRequest createRequest(User employee, LeaveType type,
                                      LocalDate startDate, LocalDate endDate,
                                      String reason, String evidenceUrl) {
        Long tenantId = TenantContext.getTenantId();
        if ((tenantId == null || tenantId == 0L) && employee != null && employee.getTenantId() != null) {
            tenantId = employee.getTenantId();
            TenantContext.setTenantId(tenantId);
        }

        if (employee == null || employee.getBranch() == null) {
            throw new IllegalArgumentException("El empleado debe tener una sucursal asignada para solicitar permisos");
        }

        Branch branch = employee.getBranch();
        if (tenantId != null && tenantId > 0L) {
            branch = branchRepository.findByIdAndTenantId(employee.getBranch().getId(), tenantId)
                    .orElse(employee.getBranch());
        }

        LeaveRequest req = LeaveRequest.builder()
                .user(employee)
                .branch(branch)
                .requestType(type)
                .startDate(startDate)
                .endDate(endDate)
                .reason(reason)
                .evidenceUrl(evidenceUrl)
                .status(LeaveStatus.PENDING)
                .build();
        if (tenantId != null) {
            req.setTenantId(tenantId);
        }

        return leaveRepository.save(req);
    }

    /** Historial de solicitudes del empleado autenticado. */
    public List<LeaveRequest> getMyRequests(Long userId) {
        return leaveRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ─── Admin ────────────────────────────────────────────────────────────────

    /** Todas las solicitudes de la sucursal (para tabla admin). */
    public List<LeaveRequest> getBranchRequests(Long branchId) {
        return leaveRepository.findByBranchIdOrderByCreatedAtDesc(branchId);
    }

    /** Solo las solicitudes PENDING de la sucursal. */
    public List<LeaveRequest> getPendingRequests(Long branchId) {
        return leaveRepository.findByBranchIdAndStatusOrderByCreatedAtDesc(branchId, LeaveStatus.PENDING);
    }

    /** Aprueba una solicitud y agrega notas opcionales del admin. */
    @Transactional
    public LeaveRequest approve(Long requestId, Long adminId, String adminNotes) {
        LeaveRequest req = findOrThrow(requestId);
        req.setStatus(LeaveStatus.APPROVED);
        req.setAdminNotes(adminNotes);
        req.setReviewedBy(adminId);
        return leaveRepository.save(req);
    }

    /** Rechaza una solicitud con notas obligatorias del admin. */
    @Transactional
    public LeaveRequest reject(Long requestId, Long adminId, String adminNotes) {
        LeaveRequest req = findOrThrow(requestId);
        req.setStatus(LeaveStatus.REJECTED);
        req.setAdminNotes(adminNotes);
        req.setReviewedBy(adminId);
        return leaveRepository.save(req);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private LeaveRequest findOrThrow(Long id) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId == 0L) {
            return leaveRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada"));
        }
        return leaveRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada o acceso denegado"));
    }

    /** Acceso público para que el controller pueda validar scope de sucursal. */
    public LeaveRequest findByIdWithTenant(Long id) {
        return findOrThrow(id);
    }
}
