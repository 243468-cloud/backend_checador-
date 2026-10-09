package com.checador.integration;

import com.checador.entity.*;
import com.checador.repository.*;
import com.checador.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TenantIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private ScheduleRosterRepository scheduleRosterRepository;

    @Autowired
    private com.checador.repository.TenantSettingsRepository tenantSettingsRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tokenTenantA;
    private Long branchIdTenantB;
    private Long employeeIdTenantB;
    private Long attendanceIdTenantB;
    private Long leaveRequestIdTenantB;
    private Long rosterIdTenantB;

    @BeforeEach
    public void setup() {
        attendanceRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        scheduleRosterRepository.deleteAll();
        userRepository.deleteAll();
        branchRepository.deleteAll();
        tenantSettingsRepository.deleteAll();

        // Crear Tenant A
        Branch branchA = new Branch();
        branchA.setName("Sucursal A");
        branchA.setTenantId(1L);
        branchA.setLatitude(19.4326);
        branchA.setLongitude(-99.1332);
        branchA.setRadiusMeters(100);
        System.out.println("DEBUG Test: Before save branchA, tenantId=" + branchA.getTenantId());
        branchA = branchRepository.save(branchA);
        System.out.println("DEBUG Test: After save branchA, tenantId=" + branchA.getTenantId());

        User adminA = new User();
        adminA.setUsername("adminA");
        adminA.setPassword(passwordEncoder.encode("password"));
        adminA.setFullName("Admin A");
        adminA.setRole(Role.ADMIN);
        adminA.setBranch(branchA);
        adminA.setTenantId(1L);
        adminA = userRepository.save(adminA);

        tokenTenantA = jwtService.generateToken(adminA);

        // Crear Tenant B
        Branch branchB = new Branch();
        branchB.setName("Sucursal B");
        branchB.setTenantId(2L);
        branchB.setLatitude(20.6597);
        branchB.setLongitude(-103.3500);
        branchB.setRadiusMeters(100);
        branchB = branchRepository.save(branchB);
        branchIdTenantB = branchB.getId();

        User employeeB = new User();
        employeeB.setUsername("employeeB");
        employeeB.setPassword(passwordEncoder.encode("password"));
        employeeB.setFullName("Employee B");
        employeeB.setRole(Role.EMPLOYEE);
        employeeB.setBranch(branchB);
        employeeB.setTenantId(2L);
        employeeB = userRepository.save(employeeB);
        employeeIdTenantB = employeeB.getId();

        Attendance attendanceB = new Attendance();
        attendanceB.setUser(employeeB);
        attendanceB.setBranch(branchB);
        attendanceB.setAttendanceDate(LocalDate.now());
        attendanceB.setCheckInTime(LocalDateTime.now());
        attendanceB.setTenantId(2L);
        attendanceB = attendanceRepository.save(attendanceB);
        attendanceIdTenantB = attendanceB.getId();

        LeaveRequest leaveB = new LeaveRequest();
        leaveB.setUser(employeeB);
        leaveB.setBranch(branchB);
        leaveB.setRequestType(LeaveRequest.LeaveType.PERMISO);
        leaveB.setStartDate(LocalDate.now());
        leaveB.setEndDate(LocalDate.now().plusDays(1));
        leaveB.setReason("Test");
        leaveB.setStatus(LeaveRequest.LeaveStatus.PENDING);
        leaveB.setTenantId(2L);
        leaveB = leaveRequestRepository.save(leaveB);
        leaveRequestIdTenantB = leaveB.getId();

        ScheduleRoster rosterB = new ScheduleRoster();
        rosterB.setBranch(branchB);
        rosterB.setRowKey("r-1");
        rosterB.setAreaName("COCINA");
        rosterB.setDayIndex(0);
        rosterB.setEmployeeName("Employee B");
        rosterB.setStatusType(ScheduleRoster.RosterStatus.NORMAL);
        rosterB.setTenantId(2L);
        rosterB.setWeekStart(LocalDate.now());
        rosterB = scheduleRosterRepository.save(rosterB);
        rosterIdTenantB = rosterB.getId();

        // Seed TenantSettings
        com.checador.entity.TenantSettings tsA = new com.checador.entity.TenantSettings();
        tsA.setTenantId(1L);
        tsA.setCompanyName("Tenant A");
        tsA.setSlug("tenant-a");
        tsA.setActiveModules(new java.util.HashSet<>(java.util.List.of("schedules", "lateArrivals", "reports", "rankings")));
        tenantSettingsRepository.save(tsA);

        com.checador.entity.TenantSettings tsB = new com.checador.entity.TenantSettings();
        tsB.setTenantId(2L);
        tsB.setCompanyName("Tenant B");
        tsB.setSlug("tenant-b");
        // Tenant B DOES NOT have 'rankings' or 'reports'
        tsB.setActiveModules(new java.util.HashSet<>(java.util.List.of("schedules")));
        tenantSettingsRepository.save(tsB);
    }

    // --- Branch ---
    @Test
    public void branchIsolation() throws Exception {
        mockMvc.perform(get("/api/branches/" + branchIdTenantB)
                .header("Authorization", "Bearer " + tokenTenantA))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(put("/api/branches/" + branchIdTenantB)
                .header("Authorization", "Bearer " + tokenTenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Update\"}"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(delete("/api/branches/" + branchIdTenantB)
                .header("Authorization", "Bearer " + tokenTenantA))
                .andExpect(status().is4xxClientError());
    }

    // --- User ---
    @Test
    public void userIsolation() throws Exception {
        mockMvc.perform(get("/api/employees/" + employeeIdTenantB)
                .header("Authorization", "Bearer " + tokenTenantA))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(put("/api/employees/" + employeeIdTenantB)
                .header("Authorization", "Bearer " + tokenTenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"Update\"}"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(delete("/api/employees/" + employeeIdTenantB)
                .header("Authorization", "Bearer " + tokenTenantA))
                .andExpect(status().is4xxClientError());
    }

    // --- Attendance ---
    @Test
    public void attendanceIsolation() throws Exception {
        // Attendance doesn't have a direct get by ID, but it has update and delete
        mockMvc.perform(put("/api/attendance/admin/" + attendanceIdTenantB)
                .header("Authorization", "Bearer " + tokenTenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(delete("/api/attendance/admin/" + attendanceIdTenantB)
                .header("Authorization", "Bearer " + tokenTenantA))
                .andExpect(status().is4xxClientError());
    }

    // --- LeaveRequest ---
    @Test
    public void leaveRequestIsolation() throws Exception {
        // Evaluate
        mockMvc.perform(put("/api/leaves/" + leaveRequestIdTenantB + "/approve")
                .header("Authorization", "Bearer " + tokenTenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    // --- ScheduleRoster ---
    @Test
    public void scheduleRosterIsolation() throws Exception {
        // Fetch schedule by branchId B using tenant A token
        mockMvc.perform(get("/api/schedules?branchId=" + branchIdTenantB + "&weekStart=" + LocalDate.now())
                .header("Authorization", "Bearer " + tokenTenantA))
                .andExpect(status().is4xxClientError());
        
        // Save roster to branchId B
        mockMvc.perform(post("/api/schedules/save")
                .header("Authorization", "Bearer " + tokenTenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":" + branchIdTenantB + ", \"weekStart\":\"" + LocalDate.now() + "\", \"cells\":[]}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    public void testModuleIsolation() throws Exception {
        // Tenant A has 'rankings' enabled
        mockMvc.perform(get("/api/ranking")
                .header("Authorization", "Bearer " + tokenTenantA))
                .andExpect(status().isOk());

        // Tenant B DOES NOT have 'rankings' enabled
        // We need a token for Tenant B to test it
        User employeeB = userRepository.findByUsername("employeeB").orElseThrow();
        String tokenTenantB = jwtService.generateToken(employeeB);

        mockMvc.perform(get("/api/ranking")
                .header("Authorization", "Bearer " + tokenTenantB))
                .andExpect(status().isForbidden());
    }
}
