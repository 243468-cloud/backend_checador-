package com.checador.integration;

import com.checador.entity.Role;
import com.checador.entity.TenantSettings;
import com.checador.entity.User;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MultiTenantLoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantSettingsRepository tenantSettingsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Long TENANT_A = 101L;
    private static final Long TENANT_B = 102L;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM attendances");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM tenant_settings");

        try {
            // Configurar y guardar Empresa A y Usuario A
            TenantContext.setTenantId(TENANT_A);
            TenantSettings settingsA = TenantSettings.builder()
                    .companyName("Empresa Alfa")
                    .slug("empresa-a")
                    .build();
            settingsA.setTenantId(TENANT_A);
            tenantSettingsRepository.save(settingsA);

            User userA = User.builder()
                    .username("juan")
                    .password(passwordEncoder.encode("PassAlfa123!"))
                    .fullName("Juan Alfa")
                    .email("juan@alfa.test")
                    .role(Role.EMPLOYEE)
                    .active(true)
                    .build();
            userA.setTenantId(TENANT_A);
            userRepository.save(userA);

            // Configurar y guardar Empresa B y Usuario B
            TenantContext.setTenantId(TENANT_B);
            TenantSettings settingsB = TenantSettings.builder()
                    .companyName("Empresa Beta")
                    .slug("empresa-b")
                    .build();
            settingsB.setTenantId(TENANT_B);
            tenantSettingsRepository.save(settingsB);

            User userB = User.builder()
                    .username("juan")
                    .password(passwordEncoder.encode("PassBeta456!"))
                    .fullName("Juan Beta")
                    .email("juan@beta.test")
                    .role(Role.EMPLOYEE)
                    .active(true)
                    .build();
            userB.setTenantId(TENANT_B);
            userRepository.save(userB);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Juan inicia sesión en Empresa A con sus credenciales y recibe tenantId de Empresa A")
    void testLoginTenantASuccess() throws Exception {
        String body = """
            {
                "username": "juan",
                "password": "PassAlfa123!",
                "slug": "empresa-a"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_A))
                .andExpect(jsonPath("$.tenantSlug").value("empresa-a"))
                .andExpect(jsonPath("$.fullName").value("Juan Alfa"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Juan no puede entrar a Empresa A con la contraseña de Empresa B")
    void testLoginTenantAWithTenantBCredentialsFails() throws Exception {
        String body = """
            {
                "username": "juan",
                "password": "PassBeta456!",
                "slug": "empresa-a"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Juan inicia sesión en Empresa B con sus credenciales y recibe tenantId de Empresa B")
    void testLoginTenantBSuccess() throws Exception {
        String body = """
            {
                "username": "juan",
                "password": "PassBeta456!",
                "slug": "empresa-b"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_B))
                .andExpect(jsonPath("$.tenantSlug").value("empresa-b"))
                .andExpect(jsonPath("$.fullName").value("Juan Beta"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Juan no puede entrar a Empresa B con la contraseña de Empresa A")
    void testLoginTenantBWithTenantACredentialsFails() throws Exception {
        String body = """
            {
                "username": "juan",
                "password": "PassAlfa123!",
                "slug": "empresa-b"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Juan sin slug es rechazado al existir en múltiples empresas y pide especificar el slug")
    void testLoginAmbiguousUsernameWithoutSlugFails() throws Exception {
        String body = """
            {
                "username": "juan",
                "password": "PassAlfa123!"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Este usuario pertenece a múltiples empresas. Por favor ingresa desde el enlace de tu empresa o especifica el identificador (slug)."));
    }
}
