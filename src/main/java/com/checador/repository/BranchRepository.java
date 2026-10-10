package com.checador.repository;

import com.checador.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends BaseTenantRepository<Branch, Long> {

    List<Branch> findByActive(Boolean active);

    Optional<Branch> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByName(String name);
}
