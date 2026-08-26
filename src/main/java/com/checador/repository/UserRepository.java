package com.checador.repository;

import com.checador.entity.User;
import com.checador.entity.Role;
import com.checador.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.branch WHERE u.username = :username")
    Optional<User> findByUsername(@Param("username") String username);

    boolean existsByUsername(String username);

    List<User> findByRole(Role role);

    List<User> findByBranchAndRoleAndActive(Branch branch, Role role, Boolean active);

    List<User> findByBranchIdAndRole(Long branchId, Role role);

    @Query("SELECT u FROM User u WHERE (:branchId IS NULL OR u.branch.id = :branchId) AND u.role = 'EMPLOYEE' AND u.active = true")
    List<User> findActiveEmployeesByBranch(@Param("branchId") Long branchId);

    List<User> findByRoleAndActive(Role role, Boolean active);
}
