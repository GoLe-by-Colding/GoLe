plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.gole"
version = "0.0.1-SNAPSHOT"
description = "GoLe LEGO Marketplace API (hexagonal)"

java {
    toolchain {
        // Java 25 LTS (로컬에 Temurin 25 설치됨). Spring Boot 4는 Java 17~26 지원.
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

extra["testcontainersVersion"] = "1.20.6"

dependencies {
    // Web / Validation / Actuator
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // AOP (클린코드: 로깅/트랜잭션/감사 등 횡단 관심사 분리)
    // Spring Boot 4에는 starter-aop가 없어 aspectjweaver를 직접 사용한다.
    // spring-aop는 spring-context를 통해 전이 포함되고, AopAutoConfiguration이 기본 활성.
    implementation("org.aspectj:aspectjweaver")

    // Data: MongoDB(primary) + Redis(cache/chat/ranking)
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mongodb")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
