package com.checador;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.checador")
public class ArchitectureTest {

    @ArchTest
    public static final ArchRule preventDirectByIdMethods = noClasses()
            .that().resideInAPackage("..service..")
            .should().callMethodWhere(new DescribedPredicate<JavaCall<?>>("calls to findById etc.") {
                @Override
                public boolean test(JavaCall<?> call) {
                    String name = call.getTarget().getName();
                    String owner = call.getTarget().getOwner().getName();
                    return name.matches("findById|getById|getReferenceById|findAllById|deleteById|existsById")
                            && owner.contains("Repository");
                }
            });
}
