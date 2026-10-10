package com.checador.config;

import com.checador.entity.Branch;
import com.checador.entity.Role;
import com.checador.entity.ShiftType;
import com.checador.entity.User;
import com.checador.repository.BranchRepository;
import com.checador.repository.TenantSettingsRepository;
import com.checador.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @org.springframework.beans.factory.annotation.Value("${app.seed.superadmin.username:superadmin}")
    private String adminUsername;

    @org.springframework.beans.factory.annotation.Value("${app.seed.superadmin.password:}")
    private String adminPassword;

    @org.springframework.beans.factory.annotation.Value("${app.seed.superadmin.email:admin@example.com}")
    private String adminEmail;

    @Override
    public void run(String... args) {
        // Asegurar que la columna shift_type en la base de datos sea VARCHAR(50) para soportar MIXED
        try {
            jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN shift_type VARCHAR(50) NULL");
            jdbcTemplate.execute("ALTER TABLE attendances MODIFY COLUMN shift_type VARCHAR(50) NULL");
            jdbcTemplate.execute("ALTER TABLE schedule_rosters MODIFY COLUMN shift_type VARCHAR(50) NULL");
            log.info(" Base de datos actualizada: shift_type ampliado a VARCHAR(50).");
        } catch (Exception e) {
            log.info("Aviso al actualizar columnas shift_type: {}", e.getMessage());
        }

        try {
            com.checador.security.TenantContext.setTenantId(0L);
            // Crear superusuario si no existe y si se proveyó una contraseña
            if (userRepository.existsByRole(Role.SUPERUSER)) {
                log.info("Superusuario ya existe. Omitiendo seed.");
                return;
            }

            if (adminPassword == null || adminPassword.isBlank()) {
                adminPassword = java.util.UUID.randomUUID().toString().substring(0, 8);
                log.warn("=========================================================");
                log.warn(" NO SE PROVEYÓ CONTRASEÑA DE SUPERADMIN (app.seed.superadmin.password).");
                log.warn(" SE HA GENERADO UNA CONTRASEÑA ALEATORIA PARA EL SUPERADMIN: {}", adminPassword);
                log.warn(" POR FAVOR GUÁRDALA O CÁMBIALA INMEDIATAMENTE.");
                log.warn("=========================================================");
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

            log.info(" Superusuario '{}' creado exitosamente.", adminUsername);
        } finally {
            com.checador.security.TenantContext.clear();
        }
    }
}
