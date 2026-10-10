package com.checador.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import com.checador.security.TenantContext;

import org.hibernate.annotations.Filter;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @PrePersist
    public void onPrePersist() {
        System.out.println("DEBUG BaseEntity.onPrePersist: tenantId=" + tenantId);
        if (tenantId == null) {
            Long currentTenantId = TenantContext.getTenantId();
            if (currentTenantId != null) {
                tenantId = currentTenantId;
            } else {
                // Para operaciones sin contexto HTTP, opcionalmente puedes establecer un valor por defecto o lanzar error.
                System.out.println("DEBUG BaseEntity.onPrePersist: currentTenantId is also null!");
            }
        }
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
