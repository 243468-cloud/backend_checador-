package com.checador.service;

import com.checador.entity.Branch;
import com.checador.entity.Role;
import com.checador.entity.User;
import com.checador.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
    }

    @Transactional
    public User createAdmin(String username, String password, String fullName, String email, Branch branch) {
        String cleanUsername = username != null ? username.trim().toLowerCase() : "";
        String cleanPassword = password != null ? password.trim() : "";
        if (userRepository.existsByUsername(cleanUsername)) {
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
        String cleanUsername = username != null ? username.trim().toLowerCase() : "";
        String cleanPassword = password != null ? password.trim() : "";
        if (userRepository.existsByUsername(cleanUsername)) {
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
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        user.setFullName(fullName);
        user.setEmail(email);
        if (shiftType != null) user.setShiftType(shiftType);
        if (profilePicture != null) user.setProfilePicture(profilePicture);
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void toggleActive(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        user.setActive(!user.getActive());
        userRepository.save(user);
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}
