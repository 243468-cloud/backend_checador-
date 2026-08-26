package com.checador.config;

import com.checador.entity.Branch;
import com.checador.entity.Role;
import com.checador.entity.ShiftType;
import com.checador.entity.User;
import com.checador.repository.BranchRepository;
import com.checador.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Asegurar que exista al menos la sucursal Via Gourmet
        Branch branch;
        if (branchRepository.count() == 0) {
            branch = branchRepository.save(Branch.builder()
                    .name("Via Gourmet")
                    .address("16a Pte. Nte. 304, Av. Cedros 402, Local 9, Fracc. Arboledas, C.P. 29030, Tuxtla Gutiérrez, Chis.")
                    .latitude(16.7599)
                    .longitude(-93.1319)
                    .radiusMeters(150)
                    .toleranceMinutes(10)
                    .active(true)
                    .build());
            log.info("✅ Sucursal Via Gourmet creada automáticamente.");
        } else {
            branch = branchRepository.findAll().get(0);
        }

        // Solo crear usuarios demo si no existe el superusuario humberto
        if (userRepository.existsByUsername("humberto")) {
            log.info("Superusuario humberto ya existe.");
            return;
        }

        log.info("Inicializando usuarios de prueba...");

        // Crear Superusuario Humberto
        userRepository.save(User.builder()
                .username("humberto")
                .password(passwordEncoder.encode("Arboledas2016"))
                .fullName("Humberto")
                .email("humberto@viagourmet.com")
                .role(Role.SUPERUSER)
                .active(true)
                .build());

        // Crear Administrador
        userRepository.save(User.builder()
                .username("admin1")
                .password(passwordEncoder.encode("Admin@2024"))
                .fullName("Administrador Central")
                .email("admin@checador.com")
                .role(Role.ADMIN)
                .branch(branch)
                .active(true)
                .build());

        // Crear Empleados de prueba
        userRepository.save(User.builder()
                .username("empleado1")
                .password(passwordEncoder.encode("Emp@2024"))
                .fullName("Juan Pérez García")
                .email("juan@checador.com")
                .role(Role.EMPLOYEE)
                .branch(branch)
                .shiftType(ShiftType.MORNING)
                .active(true)
                .build());

        userRepository.save(User.builder()
                .username("empleado2")
                .password(passwordEncoder.encode("Emp@2024"))
                .fullName("María López Sánchez")
                .email("maria@checador.com")
                .role(Role.EMPLOYEE)
                .branch(branch)
                .shiftType(ShiftType.EVENING)
                .active(true)
                .build());

        userRepository.save(User.builder()
                .username("empleado3")
                .password(passwordEncoder.encode("Emp@2024"))
                .fullName("Carlos Ramírez Torres")
                .email("carlos@checador.com")
                .role(Role.EMPLOYEE)
                .branch(branch)
                .shiftType(ShiftType.SUNDAY)
                .active(true)
                .build());

        log.info("✅ Datos de prueba creados exitosamente.");
        log.info("ℹ️  Usuarios demo creados: superadmin, admin1, empleado1, empleado2, empleado3");
        log.info("⚠️  Consulta el archivo .env.example para conocer las contraseñas de demo.");
    }
}
