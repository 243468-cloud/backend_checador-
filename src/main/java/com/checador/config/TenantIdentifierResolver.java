package com.checador.config;

import com.checador.security.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<Long> {

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            if (!TenantContext.systemReady) {
                return -1L;
            }
            throw new IllegalStateException("Se detectó una operación en base de datos sin un tenant en el contexto.");
        }
        return tenantId;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
