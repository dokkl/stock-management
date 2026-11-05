import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    checkstyle
    jacoco
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.dependency.check)
}

group = "com.stockmanagement"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${libs.versions.spring.ai.get()}")
        mavenBom("org.testcontainers:testcontainers-bom:${libs.versions.testcontainers.get()}")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation(libs.spring.ai.anthropic)
    implementation(libs.spring.ai.openai)
    implementation(libs.htmx)
    implementation(libs.webjars.locator)
    runtimeOnly("org.postgresql:postgresql")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    errorprone(libs.errorprone.core)
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation(libs.archunit)
            }
        }

        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(sourceSets.test.get().output)
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("org.springframework.boot:spring-boot-starter-webmvc-test")
                implementation("org.springframework.boot:spring-boot-starter-security-test")
                implementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
                implementation("org.springframework.boot:spring-boot-testcontainers")
                implementation("org.springframework.boot:spring-boot-starter-jdbc")
                implementation("org.testcontainers:testcontainers-postgresql")
                implementation("org.testcontainers:testcontainers-junit-jupiter")
                implementation(libs.wiremock)
            }
            targets.all {
                testTask.configure { shouldRunAfter(test) }
            }
        }

        val e2eTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(sourceSets.test.get().output)
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("org.springframework.boot:spring-boot-testcontainers")
                implementation("org.springframework.boot:spring-boot-starter-jdbc")
                implementation("org.testcontainers:testcontainers-postgresql")
                implementation(libs.playwright)
                implementation(libs.axe.playwright)
                implementation(libs.wiremock)
            }
            targets.all {
                testTask.configure { shouldRunAfter(integrationTest) }
            }
        }
    }
}

configurations {
    named("integrationTestImplementation") { extendsFrom(configurations.implementation.get()) }
    named("integrationTestRuntimeOnly") { extendsFrom(configurations.runtimeOnly.get()) }
    named("e2eTestImplementation") { extendsFrom(configurations.implementation.get()) }
    named("e2eTestRuntimeOnly") { extendsFrom(configurations.runtimeOnly.get()) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-Werror", "-parameters"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/build/generated/.*"
    }
}

tasks.withType<Test>().configureEach {
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    systemProperty("user.timezone", "UTC")
}

// --- 품질 게이트: 포맷 (헌법 원칙 I) ---
spotless {
    java {
        target("src/*/java/**/*.java")
        palantirJavaFormat(libs.versions.palantir.java.format.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// --- 품질 게이트: 정적 검사 (헌법 원칙 I) ---
checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    configFile = file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
    isIgnoreFailures = false
}

// --- 품질 게이트: 커버리지 (헌법 원칙 II) ---
val coverageExcludes = listOf("**/StockManagementApplication*")

tasks.jacocoTestReport {
    dependsOn(tasks.named("test"), tasks.named("integrationTest"), tasks.named("e2eTest"))
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec"))
    classDirectories.setFrom(sourceSets.main.get().output.asFileTree.matching { exclude(coverageExcludes) })
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec"))
    classDirectories.setFrom(sourceSets.main.get().output.asFileTree.matching { exclude(coverageExcludes) })
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
        rule {
            element = "PACKAGE"
            includes = listOf("com.stockmanagement.*.domain*")
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.named("integrationTest"), tasks.named("e2eTest"), tasks.jacocoTestCoverageVerification)
}

// --- 품질 게이트: 보안 (헌법 Quality Gate 5) ---
dependencyCheck {
    failBuildOnCVSS = 7.0f
    nvd.apiKey = System.getenv("NVD_API_KEY")
}
