package com.checador;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.checador")
public class ArchitectureTest {

    private static final Set<String> ALLOWED_NATIVE_CLASSES = Set.of(
            "com.checador.controller.AuthController",
            "com.checador.service.WeeklyReportScheduler",
            "com.checador.service.ShiftReminderScheduler",
            "com.checador.config.DataSeeder",
            "com.checador.service.CompanyRegistrationService",
            "com.checador.service.TenantSettingsService"
    );

    /**
     * Prohíbe llamadas directas a findById, getById, getReferenceById, findAllById,
     * deleteById o existsById sobre cualquier repositorio desde controller, config, security y service.
     */
    @ArchTest
    public static final ArchRule preventDirectByIdMethods = noClasses()
            .that(new DescribedPredicate<JavaClass>("in controller, config, security, or service") {
                @Override
                public boolean test(JavaClass javaClass) {
                    String pkg = javaClass.getPackageName();
                    return pkg.contains(".controller") || pkg.contains(".config")
                            || pkg.contains(".security") || pkg.contains(".service");
                }
            })
            .should().callMethodWhere(new DescribedPredicate<JavaCall<?>>("calls to findById/deleteById etc. on repository") {
                @Override
                public boolean test(JavaCall<?> call) {
                    String name = call.getTarget().getName();
                    String owner = call.getTarget().getOwner().getName();
                    return name.matches("findById|getById|getReferenceById|findAllById|deleteById|existsById")
                            && owner.contains("Repository");
                }
            });

    /**
     * Prohíbe el uso de JdbcTemplate o EntityManager nativo fuera de la lista explícita permitida
     * (AuthController, WeeklyReportScheduler, ShiftReminderScheduler, DataSeeder, CompanyRegistrationService, TenantSettingsService).
     */
    @ArchTest
    public static final ArchRule restrictNativeJdbcAndEntityManager = noClasses()
            .that(new DescribedPredicate<JavaClass>("not in explicit allowlist") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return !ALLOWED_NATIVE_CLASSES.contains(javaClass.getName())
                            && (javaClass.getPackageName().contains(".controller")
                            || javaClass.getPackageName().contains(".config")
                            || javaClass.getPackageName().contains(".security")
                            || javaClass.getPackageName().contains(".service"));
                }
            })
            .should().dependOnClassesThat(new DescribedPredicate<JavaClass>("JdbcTemplate or EntityManager") {
                @Override
                public boolean test(JavaClass javaClass) {
                    String name = javaClass.getName();
                    return name.equals("org.springframework.jdbc.core.JdbcTemplate")
                            || name.equals("jakarta.persistence.EntityManager")
                            || name.equals("javax.persistence.EntityManager");
                }
            });
}
