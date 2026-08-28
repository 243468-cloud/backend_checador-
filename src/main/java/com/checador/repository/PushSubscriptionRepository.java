package com.checador.repository;

import com.checador.entity.PushSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, Long> {

    Optional<PushSubscriptionEntity> findByEndpoint(String endpoint);

    List<PushSubscriptionEntity> findByUserId(Long userId);

    List<PushSubscriptionEntity> findByRole(String role);

    List<PushSubscriptionEntity> findByRoleAndBranchId(String role, Long branchId);

    @Modifying
    @Query("DELETE FROM PushSubscriptionEntity p WHERE p.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
