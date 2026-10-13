package com.checador.service;

import com.checador.entity.Branch;
import com.checador.entity.Role;
import com.checador.entity.TenantSettings;
import com.checador.entity.User;
import com.checador.repository.BranchRepository;
import com.checador.repository.TenantSettingsRepository;
import com.checador.repository.UserRepository;
import com.checador.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyRegistrationService {

    private final TenantSettingsRepository tenantSettingsRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.checador.repository.TenantModuleRepository tenantModuleRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public TenantSettings registerCompany(
            String companyName,
            String slug,
            String adminFullName,
            String adminUsername,
            String adminEmail,
            String adminPassword) {
        
        Integer slugCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant_settings WHERE slug = ?", Integer.class, slug);
        if (slugCount != null && slugCount > 0) {
            throw new IllegalArgumentException("El identificador URL (slug) ya está en uso.");
        }

        // 1. Generar un nuevo tenantId (obtenemos el máximo actual y sumamos 1)
        Long currentMaxTenantId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(tenant_id), 0) FROM tenant_settings", Long.class);
        Long newTenantId = (currentMaxTenantId == null ? 0L : currentMaxTenantId) + 1;

        // 2. Establecemos el tenant en el contexto antes de abrir la transacción JPA
        TenantContext.setTenantId(newTenantId);

        try {
            return transactionTemplate.execute(status -> {
                // 3. Crear la configuración del Tenant
                TenantSettings tenantSettings = TenantSettings.builder()
                        .companyName(companyName)
                        .slug(slug)
                        .primaryColor("#4F46E5") // Color por defecto
                        .toleranceMinutes(10)
                        .subscriptionStatus(TenantSettings.SubscriptionStatus.TRIAL)
                        .activeModules(new java.util.HashSet<>(java.util.List.of("schedules", "lateArrivals")))
                        .build();
                tenantSettings.setTenantId(newTenantId);
                tenantSettings = tenantSettingsRepository.save(tenantSettings);

                if (tenantSettings.getActiveModules() != null) {
                    var moduleEntities = tenantSettings.getActiveModules().stream()
                            .map(m -> new com.checador.entity.TenantModule(newTenantId, m))
                            .toList();
                    tenantModuleRepository.saveAll(moduleEntities);
                }

                // 4. Crear la Sucursal Matriz
                Branch mainBranch = Branch.builder()
                        .name("Matriz")
                        .address("Dirección principal")
                        .latitude(0.0) // Deben configurarlo luego
                        .longitude(0.0)
                        .radiusMeters(500)
                        .toleranceMinutes(10)
                        .active(true)
                        .build();
                mainBranch.setTenantId(newTenantId);
                mainBranch = branchRepository.save(mainBranch);

                // 5. Crear el Usuario Administrador
                User adminUser = User.builder()
                        .username(adminUsername)
                        .password(passwordEncoder.encode(adminPassword))
                        .fullName(adminFullName)
                        .email(adminEmail)
                        .role(Role.ADMIN) // Le damos permisos de administrador
                        .branch(mainBranch)
                        .active(true)
                        .build();
                adminUser.setTenantId(newTenantId);
                userRepository.save(adminUser);

                return tenantSettings;
            });
        } finally {
            // 6. Limpiamos el contexto para no afectar otras peticiones
            TenantContext.clear();
        }
    }
}
