package com.checador.integration;

import com.checador.entity.Branch;
import com.checador.entity.Role;
import com.checador.entity.TenantSettings;
import com.checador.entity.User;
import com.checador.repository.BranchRepository;
import com.checador.repository.TenantSettingsRepository;
import com.checador.repository.UserRepository;
import com.checador.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private TenantSettingsRepository tenantSettingsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final Long TENANT_A = 201L;
    private static final Long TENANT_B = 202L;

    private Long branchIdA;
    private Long branchIdB;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM attendances");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM branches");
        jdbcTemplate.execute("DELETE FROM tenant_settings");

        try {
            // Empresa A
            TenantContext.setTenantId(TENANT_A);
            TenantSettings settingsA = TenantSettings.builder()
                    .companyName("Empresa A")
                    .slug("emp-a")
                    .invitationCodeHash(passwordEncoder.encode("CODE-A-123"))
                    .build();
            settingsA.setTenantId(TENANT_A);
            tenantSettingsRepository.save(settingsA);

            Branch branchA = Branch.builder()
                    .name("Sucursal A")
                    .latitude(19.43)
                    .longitude(-99.13)
                    .radiusMeters(100)
                    .toleranceMinutes(10)
                    .active(true)
                    .build();
            branchA.setTenantId(TENANT_A);
            branchIdA = branchRepository.save(branchA).getId();

            // Empresa B
            TenantContext.setTenantId(TENANT_B);
            TenantSettings settingsB = TenantSettings.builder()
                    .companyName("Empresa B")
                    .slug("emp-b")
                    .invitationCodeHash(passwordEncoder.encode("CODE-B-456"))
                    .build();
            settingsB.setTenantId(TENANT_B);
            tenantSettingsRepository.save(settingsB);

            Branch branchB = Branch.builder()
                    .name("Sucursal B")
                    .latitude(20.65)
                    .longitude(-99.20)
                    .radiusMeters(100)
                    .toleranceMinutes(10)
                    .active(true)
                    .build();
            branchB.setTenantId(TENANT_B);
            branchIdB = branchRepository.save(branchB).getId();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Registro sin código de invitación es rechazado (4xx)")
    void testRegisterWithoutInvitationCodeFails4xx() throws Exception {
        String body = String.format("""
            {
                "username": "nuevo_emp",
                "password": "Password123!",
                "fullName": "Nuevo Empleado",
                "email": "emp@test.com",
                "branchId": %d,
                "shiftType": "MORNING",
                "slug": "emp-a",
                "invitationCode": ""
            }
            """, branchIdA);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Registro con código de otra empresa es rechazado (4xx)")
    void testRegisterWithOtherCompanyCodeFails4xx() throws Exception {
        String body = String.format("""
            {
                "username": "nuevo_emp",
                "password": "Password123!",
                "fullName": "Nuevo Empleado",
                "email": "emp@test.com",
                "branchId": %d,
                "shiftType": "MORNING",
                "slug": "emp-a",
                "invitationCode": "CODE-B-456"
            }
            """, branchIdA);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invitación o empresa inválida."));
    }

    @Test
    @DisplayName("Registro con branchId de otra empresa es rechazado (4xx)")
    void testRegisterWithForeignBranchIdFails4xx() throws Exception {
        String body = String.format("""
            {
                "username": "nuevo_emp",
                "password": "Password123!",
                "fullName": "Nuevo Empleado",
                "email": "emp@test.com",
                "branchId": %d,
                "shiftType": "MORNING",
                "slug": "emp-a",
                "invitationCode": "CODE-A-123"
            }
            """, branchIdB); // branchId de Empresa B con código de Empresa A

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invitación o empresa inválida."));
    }

    @Test
    @DisplayName("Intento de inyectar role=ADMIN o tenantId es ignorado y fuerza EMPLOYEE y tenant correcto")
    void testRegisterPrivilegeEscalationIgnored() throws Exception {
        String body = String.format("""
            {
                "username": "hacker_emp",
                "password": "Password123!",
                "fullName": "Hacker Intent",
                "email": "hacker@test.com",
                "branchId": %d,
                "shiftType": "MORNING",
                "slug": "emp-a",
                "invitationCode": "CODE-A-123",
                "role": "ADMIN",
                "tenantId": 999,
                "active": false
            }
            """, branchIdA);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A));

        try {
            TenantContext.setTenantId(TENANT_A);
            User saved = userRepository.findByUsernameAndTenantId("hacker_emp", TENANT_A)
                    .orElseThrow(() -> new AssertionError("Usuario debe existir en Empresa A"));

            assertEquals(Role.EMPLOYEE, saved.getRole(), "El rol debe forzarse a EMPLOYEE");
            assertEquals(TENANT_A, saved.getTenantId(), "El tenantId debe forzarse al de la empresa");
            assertTrue(saved.getActive(), "El usuario debe quedar activo");
        } finally {
            TenantContext.clear();
        }
    }
}
