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
        // Solo crear datos si no existe el superusuario
        if (userRepository.existsByUsername("superadmin")) {
            log.info("Datos de inicio ya existen, omitiendo seeder.");
            return;
        }

        log.info("Inicializando datos de prueba...");

        // Crear sucursal principal
        Branch branch = branchRepository.save(Branch.builder()
                .name("Sucursal Central")
                .address("Av. Principal #100, Ciudad")
                .latitude(19.4326)   // Ciudad de México (demo)
                .longitude(-99.1332)
                .radiusMeters(200)
                .toleranceMinutes(10)
                .active(true)
                .build());

        // Crear Superusuario
        userRepository.save(User.builder()
                .username("superadmin")
                .password(passwordEncoder.encode("Super@2024"))
                .fullName("Super Administrador")
                .email("super@checador.com")
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
