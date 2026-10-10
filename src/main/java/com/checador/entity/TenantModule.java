package com.checador.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Entity
@Table(name = "tenant_modules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(TenantModule.TenantModuleId.class)
public class TenantModule {

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Id
    @Column(name = "module_name")
    private String moduleName;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantModuleId implements Serializable {
        private Long tenantId;
        private String moduleName;
    }
}
