package com.checador.repository;

import com.checador.entity.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, Long> {
    Optional<TenantSettings> findBySlug(String slug);
    Optional<TenantSettings> findByTenantId(Long tenantId);
}
