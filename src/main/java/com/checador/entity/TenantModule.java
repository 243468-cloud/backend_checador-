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
@IdClass(TenantModule.TenantModuleId.class)
public class TenantModule {

    public TenantModule() {
    }

    public TenantModule(Long tenantId, String moduleName) {
        this.tenantId = tenantId;
        this.moduleName = moduleName;
    }

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Id
    @Column(name = "module_name")
    private String moduleName;

    @Data
    public static class TenantModuleId implements Serializable {
        public TenantModuleId() {
        }

        public TenantModuleId(Long tenantId, String moduleName) {
            this.tenantId = tenantId;
            this.moduleName = moduleName;
        }

        private Long tenantId;
        private String moduleName;
    }
}
