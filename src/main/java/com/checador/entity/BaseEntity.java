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
@FilterDef(name = "tenantFilter", parameters = {@ParamDef(name = "tenantId", type = Long.class)})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @PrePersist
    public void onPrePersist() {
        if (tenantId == null) {
            Long currentTenantId = TenantContext.getTenantId();
            if (currentTenantId != null) {
                tenantId = currentTenantId;
            } else {
                // Para operaciones sin contexto HTTP, opcionalmente puedes establecer un valor por defecto o lanzar error.
            }
        }
    }
}
