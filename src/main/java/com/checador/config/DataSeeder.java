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
        // Asegurar que exista al menos la sucursal Via Gourmet
        Branch branch;
        if (branchRepository.count() == 0) {
            branch = Branch.builder()
                    .name("Via Gourmet")
                    .address("16a Pte. Nte. 304, Av. Cedros 402, Local 9, Fracc. Arboledas, C.P. 29030, Tuxtla Gutiérrez, Chis.")
                    .latitude(16.7599)
                    .longitude(-93.1319)
                    .radiusMeters(500)
                    .toleranceMinutes(10)
                    .active(true)
                    .build();
            branch.setTenantId(1L);
            branch = branchRepository.save(branch);
            log.info(" Sucursal Via Gourmet creada automáticamente.");
        } else {
            branch = branchRepository.findAll().get(0);
            if (branch.getRadiusMeters() < 500) {
                branch.setRadiusMeters(500);
                branchRepository.save(branch);
            }
        }

        // Seed TenantSettings for Via Gourmet (Tenant 1)
        if (tenantSettingsRepository.findByTenantId(1L).isEmpty()) {
            com.checador.entity.TenantSettings settings = com.checador.entity.TenantSettings.builder()
                    .companyName("Vía Gourmet")
                    .slug("via-gourmet")
                    .primaryColor("#4F46E5")
                    .toleranceMinutes(10)
                    .subscriptionStatus(com.checador.entity.TenantSettings.SubscriptionStatus.ACTIVE)
                    .activeModules(new java.util.HashSet<>(java.util.List.of("schedules", "lateArrivals", "reports", "rankings")))
                    .build();
            settings.setTenantId(1L);
            tenantSettingsRepository.save(settings);
            log.info(" Configuración de Vía Gourmet creada automáticamente.");
        } else {
            // Asegurar que tengan todos los módulos
            com.checador.entity.TenantSettings settings = tenantSettingsRepository.findByTenantId(1L).get();
            if (settings.getActiveModules().size() < 4) {
                settings.getActiveModules().addAll(java.util.List.of("schedules", "lateArrivals", "reports", "rankings"));
                tenantSettingsRepository.save(settings);
            }
        }

        // Solo crear usuarios demo si no existe el superusuario humberto
        if (userRepository.existsByUsername("humberto")) {
            log.info("Superusuario humberto ya existe.");
            return;
        }

        log.info("Inicializando usuarios de prueba...");

        // Crear Superusuario Humberto
        User humberto = User.builder()
                .username("humberto")
                .password(passwordEncoder.encode("Arboledas2016"))
                .fullName("Humberto")
                .email("humberto@viagourmet.com")
                .role(Role.SUPERUSER)
                .active(true)
                .build();
        humberto.setTenantId(1L);
        userRepository.save(humberto);

        // Crear Administrador
        User admin1 = User.builder()
                .username("admin1")
                .password(passwordEncoder.encode("Admin@2024"))
                .fullName("Administrador Central")
                .email("admin@checador.com")
                .role(Role.ADMIN)
                .branch(branch)
                .active(true)
                .build();
        admin1.setTenantId(1L);
        userRepository.save(admin1);

        // Crear Empleados de prueba
        User empleado1 = User.builder()
                .username("empleado1")
                .password(passwordEncoder.encode("Emp@2024"))
                .fullName("Juan Pérez García")
                .email("juan@checador.com")
                .role(Role.EMPLOYEE)
                .branch(branch)
                .shiftType(ShiftType.MORNING)
                .active(true)
                .build();
        empleado1.setTenantId(1L);
        userRepository.save(empleado1);

        User empleado2 = User.builder()
                .username("empleado2")
                .password(passwordEncoder.encode("Emp@2024"))
                .fullName("María López Sánchez")
                .email("maria@checador.com")
                .role(Role.EMPLOYEE)
                .branch(branch)
                .shiftType(ShiftType.EVENING)
                .active(true)
                .build();
        empleado2.setTenantId(1L);
        userRepository.save(empleado2);

        User empleado3 = User.builder()
                .username("empleado3")
                .password(passwordEncoder.encode("Emp@2024"))
                .fullName("Carlos Ramírez Torres")
                .email("carlos@checador.com")
                .role(Role.EMPLOYEE)
                .branch(branch)
                .shiftType(ShiftType.SUNDAY)
                .active(true)
                .build();
        empleado3.setTenantId(1L);
        userRepository.save(empleado3);

        log.info(" Datos de prueba creados exitosamente.");
        log.info(" Usuarios demo creados: superadmin, admin1, empleado1, empleado2, empleado3");
        log.info("  Consulta el archivo .env.example para conocer las contraseñas de demo.");
    }
}
