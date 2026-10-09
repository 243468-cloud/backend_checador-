package com.checador.config;

import com.checador.security.ModuleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ModuleInterceptor moduleInterceptor;

    public WebMvcConfig(ModuleInterceptor moduleInterceptor) {
        this.moduleInterceptor = moduleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(moduleInterceptor).addPathPatterns("/api/**");
    }
}
