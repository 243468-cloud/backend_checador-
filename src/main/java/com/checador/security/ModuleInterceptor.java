package com.checador.security;

import com.checador.entity.TenantSettings;
import com.checador.service.TenantSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class ModuleInterceptor implements HandlerInterceptor {

    private final ApplicationContext applicationContext;

    public ModuleInterceptor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        RequiresModule annotation = method.getMethodAnnotation(RequiresModule.class);
        if (annotation == null) {
            annotation = method.getBeanType().getAnnotation(RequiresModule.class);
        }

        if (annotation != null) {
            String requiredModule = annotation.value();
            TenantSettingsService settingsService = applicationContext.getBean(TenantSettingsService.class);
            TenantSettings settings = settingsService.getCurrentTenantSettings();

            Set<String> modules = settings.getActiveModules();
            if (modules == null || !modules.contains(requiredModule)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "El módulo '" + requiredModule + "' no está activo para tu empresa.");
                return false;
            }
        }

        return true;
    }
}
