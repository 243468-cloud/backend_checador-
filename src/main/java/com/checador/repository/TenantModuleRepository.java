package com.checador.repository;

import com.checador.entity.TenantModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenantModuleRepository extends JpaRepository<TenantModule, TenantModule.TenantModuleId> {
    List<TenantModule> findByTenantId(Long tenantId);
    void deleteByTenantId(Long tenantId);
}
