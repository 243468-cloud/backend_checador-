package com.checador.service;

import com.checador.entity.AuditLog;
import com.checador.entity.Branch;
import com.checador.entity.User;
import com.checador.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(User performedBy, String action, String entityType, Long entityId,
                    String details, String ipAddress, Branch branch) {
        AuditLog log = AuditLog.builder()
                .performedBy(performedBy)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .ipAddress(ipAddress)
                .branch(branch)
                .build();
        auditLogRepository.save(log);
    }

    public Page<AuditLog> getGlobalLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<AuditLog> getLogsByBranch(Long branchId, Pageable pageable) {
        return auditLogRepository.findByBranchIdOrderByCreatedAtDesc(branchId, pageable);
    }
}
