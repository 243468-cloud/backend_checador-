package com.checador.service;

import com.checador.entity.Branch;
import com.checador.entity.Role;
import com.checador.entity.User;
import com.checador.repository.AttendanceRepository;
import com.checador.repository.LeaveRequestRepository;
import com.checador.repository.PushSubscriptionRepository;
import com.checador.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import com.checador.security.TenantContext;
import org.springframework.stereotype.Service;
import com.checador.exception.ResourceNotFoundException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AttendanceRepository attendanceRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AttendanceRepository attendanceRepository,
                       PushSubscriptionRepository pushSubscriptionRepository,
                       LeaveRequestRepository leaveRequestRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.attendanceRepository = attendanceRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Long tenantId = com.checador.security.TenantContext.getTenantId();
        if (tenantId != null) {
            return userRepository.findByUsernameAndTenantId(username, tenantId)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
    }

    @Transactional
    public User createAdmin(String username, String password, String fullName, String email, Branch branch) {
        String cleanUsername = username != null ? username.trim() : "";
        String cleanPassword = password != null ? password.trim() : "";
        Long tenantId = com.checador.security.TenantContext.getTenantId();
        boolean exists = (tenantId != null)
                ? userRepository.existsByUsernameAndTenantId(cleanUsername, tenantId)
                : userRepository.existsByUsername(cleanUsername);
        if (exists) {
            throw new IllegalArgumentException("El nombre de usuario ya existe");
        }
        return userRepository.save(User.builder()
                .username(cleanUsername)
                .password(passwordEncoder.encode(cleanPassword))
                .fullName(fullName != null ? fullName.trim() : "")
                .email(email != null ? email.trim() : null)
                .role(Role.ADMIN)
                .branch(branch)
                .active(true)
                .build());
    }

    @Transactional
    public User createEmployee(String username, String password, String fullName, String email,
                                Branch branch, com.checador.entity.ShiftType shiftType) {
        String cleanUsername = username != null ? username.trim() : "";
        String cleanPassword = password != null ? password.trim() : "";
        Long tenantId = com.checador.security.TenantContext.getTenantId();
        boolean exists = (tenantId != null)
                ? userRepository.existsByUsernameAndTenantId(cleanUsername, tenantId)
                : userRepository.existsByUsername(cleanUsername);
        if (exists) {
            throw new IllegalArgumentException("El nombre de usuario ya existe");
        }
        return userRepository.save(User.builder()
                .username(cleanUsername)
                .password(passwordEncoder.encode(cleanPassword))
                .fullName(fullName != null ? fullName.trim() : "")
                .email(email != null ? email.trim() : null)
                .role(Role.EMPLOYEE)
                .branch(branch)
                .shiftType(shiftType)
                .active(true)
                .build());
    }

    public List<User> getEmployeesByBranch(Long branchId) {
        return userRepository.findActiveEmployeesByBranch(branchId);
    }

    public List<User> getAdmins() {
        return userRepository.findByRoleAndActive(Role.ADMIN, true);
    }

    @Transactional
    public User updateUser(Long id, String fullName, String email,
                           com.checador.entity.ShiftType shiftType, String profilePicture) {
        User user = findByIdWithTenant(id);
        user.setFullName(fullName);
        user.setEmail(email);
        if (shiftType != null) user.setShiftType(shiftType);
        if (profilePicture != null) user.setProfilePicture(profilePicture);
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long id, String newPassword) {
        User user = findByIdWithTenant(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void toggleActive(Long id) {
        User user = findByIdWithTenant(id);
        user.setActive(!user.getActive());
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        Long tenantId = TenantContext.getTenantId();
        User user = findByIdWithTenant(id);
        attendanceRepository.deleteByUserId(id);
        pushSubscriptionRepository.deleteByUserId(id);
        leaveRequestRepository.deleteByUserId(id);
        userRepository.deleteByIdAndTenantId(user.getId(), tenantId);
    }

    public User findByIdWithTenant(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado o acceso denegado"));
    }
}
