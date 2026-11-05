package com.stockmanagement;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 헌법 원칙 I: 계층 단방향 의존과 금액 부동소수점 금지를 강제한다. */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.stockmanagement");
    }

    @Test
    void domainDependsOnNothingOutsideDomain() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..application..",
                        "..web..",
                        "..persistence..",
                        "..adapter..",
                        "org.springframework..",
                        "jakarta.persistence..")
                .check(classes);
    }

    @Test
    void webDoesNotDependOnPersistenceOrAdapters() {
        noClasses()
                .that()
                .resideInAPackage("..web..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..persistence..", "..adapter..")
                .check(classes);
    }

    @Test
    void applicationDoesNotDependOnWeb() {
        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..")
                .check(classes);
    }

    @Test
    void domainHasNoFloatingPointFields() {
        fields().that()
                .areDeclaredInClassesThat()
                .resideInAPackage("..domain..")
                .should()
                .notHaveRawType(double.class)
                .andShould()
                .notHaveRawType(float.class)
                .andShould()
                .notHaveRawType(Double.class)
                .andShould()
                .notHaveRawType(Float.class)
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void applicationAndDomainDoNotDependOnSpringSecurity() {
        noClasses()
                .that()
                .resideInAnyPackage("..application..", "..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.security..")
                .check(classes);
    }

    @Test
    void portsAreInterfaces() {
        classes()
                .that()
                .resideInAPackage("..application.port..")
                .should()
                .beInterfaces()
                .allowEmptyShould(true)
                .check(classes);
    }
}
