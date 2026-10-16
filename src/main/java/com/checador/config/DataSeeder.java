package com.checador.config;

import com.checador.entity.Branch;
import com.checador.entity.Role;
import com.checador.entity.ShiftType;
import com.checador.entity.User;
import com.checador.repository.BranchRepository;
import com.checador.repository.TenantSettingsRepository;
import com.checador.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public DataSeeder(
            UserRepository userRepository,
            BranchRepository branchRepository,
            TenantSettingsRepository tenantSettingsRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @org.springframework.beans.factory.annotation.Value("${app.seed.superadmin.username:superadmin}")
    private String adminUsername;

    @org.springframework.beans.factory.annotation.Value("${app.seed.superadmin.password:}")
    private String adminPassword;

    @org.springframework.beans.factory.annotation.Value("${app.seed.superadmin.email:admin@example.com}")
    private String adminEmail;

    @Override
    public void run(String... args) {
        try {
            com.checador.security.TenantContext.setTenantId(0L);
            // Si ya existe superusuario, omitir seed
            if (userRepository.existsByRole(Role.SUPERUSER)) {
                log.info("Superusuario ya existe. Omitiendo seed.");
                return;
            }

            if (adminPassword == null || adminPassword.isBlank()) {
                throw new IllegalStateException("Falta configurar la contraseña del superusuario inicial en app.seed.superadmin.password (o variable de entorno SUPERADMIN_PASSWORD). No se puede iniciar sin credencial de superadmin.");
            }

            User superadmin = User.builder()
                    .username(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .fullName("System Administrator")
                    .email(adminEmail)
                    .role(Role.SUPERUSER)
                    .active(true)
                    .build();
            superadmin.setTenantId(0L); // Superadmin no pertenece a ningún tenant
            userRepository.save(superadmin);

            log.info("Superusuario inicial '{}' creado exitosamente.", adminUsername);
        } finally {
            com.checador.security.TenantContext.clear();
        }
    }
}
