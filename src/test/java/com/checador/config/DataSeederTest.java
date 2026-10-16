package com.checador.config;

import com.checador.entity.Role;
import com.checador.entity.User;
import com.checador.repository.BranchRepository;
import com.checador.repository.TenantSettingsRepository;
import com.checador.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private TenantSettingsRepository tenantSettingsRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Caso 1: Si ya existe un SUPERUSER, DataSeeder omite el seed sin exigir contraseña")
    void testExistingSuperuserDoesNotRequirePassword() {
        when(userRepository.existsByRole(Role.SUPERUSER)).thenReturn(true);

        DataSeeder seeder = new DataSeeder(userRepository, branchRepository, tenantSettingsRepository, passwordEncoder, jdbcTemplate);
        ReflectionTestUtils.setField(seeder, "adminUsername", "superadmin");
        ReflectionTestUtils.setField(seeder, "adminPassword", ""); // Contraseña vacía
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@example.com");

        assertDoesNotThrow(() -> seeder.run(), "No debe lanzar excepción si el superusuario ya existe");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Caso 2: Si NO existe SUPERUSER y la contraseña está vacía o nula, lanza IllegalStateException")
    void testMissingSuperuserThrowsWhenPasswordIsEmpty() {
        when(userRepository.existsByRole(Role.SUPERUSER)).thenReturn(false);

        DataSeeder seeder = new DataSeeder(userRepository, branchRepository, tenantSettingsRepository, passwordEncoder, jdbcTemplate);
        ReflectionTestUtils.setField(seeder, "adminUsername", "superadmin");
        ReflectionTestUtils.setField(seeder, "adminPassword", null); // Contraseña nula
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@example.com");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.run());
        assertTrue(ex.getMessage().contains("SUPERADMIN_PASSWORD"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Caso 3: Si NO existe SUPERUSER y se provee contraseña, crea el superadmin con tenantId 0")
    void testMissingSuperuserCreatesSuperadminWhenPasswordIsProvided() {
        when(userRepository.existsByRole(Role.SUPERUSER)).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("encoded_pass");

        DataSeeder seeder = new DataSeeder(userRepository, branchRepository, tenantSettingsRepository, passwordEncoder, jdbcTemplate);
        ReflectionTestUtils.setField(seeder, "adminUsername", "superadmin");
        ReflectionTestUtils.setField(seeder, "adminPassword", "SecurePass123!");
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@example.com");

        assertDoesNotThrow(() -> seeder.run());
        verify(userRepository, times(1)).save(argThat(user ->
                user.getUsername().equals("superadmin") &&
                user.getRole() == Role.SUPERUSER &&
                user.getTenantId() == 0L
        ));
    }
}
