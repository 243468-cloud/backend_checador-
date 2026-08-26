package com.checador.repository;

import com.checador.entity.Notification;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Todas las notificaciones para un rol, ordenadas por fecha descendente */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.recipientRole = :role
          AND (:branchId IS NULL OR n.branchId IS NULL OR n.branchId = :branchId)
        ORDER BY n.createdAt DESC
        """)
    List<Notification> findForRole(
            @Param("role") String role,
            @Param("branchId") Long branchId,
            PageRequest page);

    /** Cuenta de notificaciones no leídas para un rol/sucursal */
    @Query("""
        SELECT COUNT(n) FROM Notification n
        WHERE n.recipientRole = :role
          AND (:branchId IS NULL OR n.branchId IS NULL OR n.branchId = :branchId)
          AND n.readAt IS NULL
        """)
    long countUnread(
            @Param("role") String role,
            @Param("branchId") Long branchId);

    /** Marca todas como leídas para un rol/sucursal */
    @Modifying
    @Query("""
        UPDATE Notification n
        SET n.readAt = CURRENT_TIMESTAMP
        WHERE n.recipientRole = :role
          AND (:branchId IS NULL OR n.branchId IS NULL OR n.branchId = :branchId)
          AND n.readAt IS NULL
        """)
    void markAllRead(
            @Param("role") String role,
            @Param("branchId") Long branchId);
}
