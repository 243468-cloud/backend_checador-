package com.checador.config;

import com.checador.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**", "/api/branches/public", "/error").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // ── Superusuario exclusivo ────────────────────────────────
                .requestMatchers("/api/branches/**").hasAnyRole("SUPERUSER")
                .requestMatchers("/api/admins/**").hasRole("SUPERUSER")
                .requestMatchers("/api/reports/global/**").hasRole("SUPERUSER")
                .requestMatchers("/api/audit/**").hasRole("SUPERUSER")

                // ── Horarios: lectura para EMPLOYEE, ADMIN y SUPERUSER; escritura solo ADMIN/SUPERUSER ──
                .requestMatchers(HttpMethod.GET,    "/api/schedules/**").hasAnyRole("EMPLOYEE", "ADMIN", "SUPERUSER")
                .requestMatchers(HttpMethod.POST,   "/api/schedules/**").hasAnyRole("ADMIN", "SUPERUSER")
                .requestMatchers(HttpMethod.DELETE, "/api/schedules/**").hasAnyRole("ADMIN", "SUPERUSER")

                // ── Eventos en Tiempo Real (SSE) y Notificaciones Push Web ─
                .requestMatchers("/api/events/**", "/api/push/**").authenticated()

                // ── Notificaciones ────────────────────────────────────────
                .requestMatchers("/api/notifications/**").hasAnyRole("ADMIN", "SUPERUSER")

                // ── Ranking & Recompensas ─────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/api/ranking/**").hasAnyRole("EMPLOYEE", "ADMIN", "SUPERUSER")
                .requestMatchers(HttpMethod.PUT, "/api/ranking/config").hasRole("SUPERUSER")

                // ── Permisos: empleado puede ver/crear los suyos; admin gestiona ──
                .requestMatchers(HttpMethod.GET,  "/api/leaves/me").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/leaves").authenticated()
                .requestMatchers("/api/leaves/admin/**").hasAnyRole("ADMIN", "SUPERUSER")
                .requestMatchers("/api/leaves/*/approve").hasAnyRole("ADMIN", "SUPERUSER")
                .requestMatchers("/api/leaves/*/reject").hasAnyRole("ADMIN", "SUPERUSER")

                // ── Admin y Superusuario ──────────────────────────────────
                .requestMatchers("/api/employees/**").hasAnyRole("ADMIN", "SUPERUSER")
                .requestMatchers("/api/reports/**").hasAnyRole("ADMIN", "SUPERUSER")
                .requestMatchers("/api/attendance/admin/**").hasAnyRole("ADMIN", "SUPERUSER")

                // ── Empleado ─────────────────────────────────────────────
                .requestMatchers("/api/attendance/checkin").hasRole("EMPLOYEE")
                .requestMatchers("/api/attendance/checkout").hasRole("EMPLOYEE")
                .requestMatchers("/api/attendance/today").hasRole("EMPLOYEE")

                .anyRequest().authenticated()
            )
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            for (String origin : allowedOrigins.split(",")) {
                config.addAllowedOriginPattern(origin.trim());
            }
        }
        config.addAllowedOriginPattern("http://localhost:*");
        config.addAllowedOriginPattern("http://127.0.0.1:*");
        config.addAllowedOriginPattern("https://*.vercel.app");
        config.addAllowedOriginPattern("https://*.onrender.com");
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
