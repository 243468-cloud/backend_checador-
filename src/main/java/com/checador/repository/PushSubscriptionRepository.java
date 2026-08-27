package com.checador.repository;

import com.checador.entity.PushSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, Long> {

    Optional<PushSubscriptionEntity> findByEndpoint(String endpoint);

    List<PushSubscriptionEntity> findByUserId(Long userId);

    List<PushSubscriptionEntity> findByRole(String role);

    List<PushSubscriptionEntity> findByRoleAndBranchId(String role, Long branchId);
}
