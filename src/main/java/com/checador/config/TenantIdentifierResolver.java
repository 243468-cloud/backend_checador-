package com.checador.config;

import com.checador.security.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<Long> {

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        // Permitir introspección de metadatos de Spring Data JPA durante la inicialización de repositorios
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().contains("PersistenceProvider") ||
                element.getClassName().contains("JpaRepositoryFactory")) {
                return 0L;
            }
        }
        throw new IllegalStateException("Se detectó una operación en base de datos sin un tenant en el contexto.");
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
