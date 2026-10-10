package com.gole.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 헥사고날 경계 검사. AGENTS.md "아키텍처 — 백엔드"의 규칙을 문서가 아니라 CI 로 강제한다.
 *
 * <p>컨텍스트는 {@code com.gole.api.<컨텍스트>} 한 단계다. {@code common} 은 컨텍스트가 아니라 횡단 관심사이고,
 * protobuf 생성 코드({@code com.gole.api.*.grpc})는 검사 대상이 아니다.
 *
 * <ul>
 *   <li>domain 은 프레임워크도, 자기 application·adapter 도 모른다.
 *   <li>application 은 adapter 와 저장소·웹 프레임워크를 모른다. 서비스는 {@code application.service} 에 둔다.
 *   <li>domain·application 은 다른 컨텍스트를 모른다. 연동은 내 아웃바운드 포트를 내 어댑터가 상대 유스케이스로
 *       위임해 구현한다({@code order/adapter/out/listing/ListingReservationAdapter} 가 표준 예시).
 *   <li>어댑터·설정·시더는 다른 컨텍스트의 인바운드 포트와 그 포트가 내주는 도메인 타입에만 의존한다.
 *   <li>인바운드 어댑터(웹)는 유스케이스만 부른다 — 서비스 구현·아웃바운드 포트·아웃바운드 어댑터를 직접 쓰지 않는다.
 *   <li>인바운드 어댑터는 트랜잭션을 걸지 않는다. 트랜잭션 경계는 유스케이스(서비스)가 정한다.
 *   <li>유스케이스는 서비스가 구현한다 — 어댑터가 인바운드 포트를 겸하지 않는다.
 *   <li>common 은 어느 컨텍스트에도 의존하지 않는다.
 * </ul>
 */
class HexagonalArchitectureTest {

    private static final String ROOT = "com.gole.api.";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.gole.api");
    }

    @Test
    @DisplayName("도메인은 프레임워크와 자기 application·adapter 를 모른다")
    void domain_isFrameworkFree() {
        check(noClasses()
                .that()
                .resideInAPackage("com.gole.api..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "org.bson..",
                        "com.mongodb..",
                        "com.fasterxml.jackson..",
                        "com.gole.api..application..",
                        "com.gole.api..adapter.."));
    }

    @Test
    @DisplayName("application 은 어댑터와 저장소(Mongo·Redis 드라이버 포함)·웹 프레임워크를 모른다")
    void application_doesNotKnowAdaptersOrInfrastructure() {
        check(noClasses()
                .that()
                .resideInAPackage("com.gole.api..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.gole.api..adapter..",
                        "org.springframework.data.mongodb..",
                        "org.springframework.data.redis..",
                        "com.mongodb..",
                        "org.bson..",
                        "org.springframework.web..",
                        "org.springframework.http..",
                        "jakarta.servlet.."));
    }

    @Test
    @DisplayName("서비스는 application.service 에 둔다 — application 바로 밑에 클래스를 두지 않는다")
    void services_liveInApplicationServicePackage() {
        check(noClasses().should().resideInAPackage("com.gole.api.*.application"));
    }

    @Test
    @DisplayName("유스케이스(인바운드 포트)는 application.service 의 서비스가 구현한다")
    void useCases_areImplementedByServices() {
        check(classes()
                .that(implementInboundPort())
                .and()
                .areNotInterfaces()
                .should()
                .resideInAPackage("com.gole.api..application.service.."));
    }

    @Test
    @DisplayName("domain·application 은 다른 컨텍스트를 모른다")
    void innerLayers_doNotKnowOtherContexts() {
        check(classes()
                .that()
                .resideInAnyPackage("com.gole.api..domain..", "com.gole.api..application..")
                .and(insideAContext())
                .should(dependOnlyOnOwnContext()));
    }

    @Test
    @DisplayName("어댑터·설정·시더는 다른 컨텍스트의 인바운드 포트와 그 도메인 타입에만 의존한다")
    void outerLayers_useOnlyInboundPortsOfOtherContexts() {
        check(classes()
                .that()
                .resideOutsideOfPackages("com.gole.api..domain..", "com.gole.api..application..")
                .and(insideAContext())
                .should(dependOnOtherContextsOnlyThroughInboundPorts()));
    }

    @Test
    @DisplayName("인바운드 어댑터는 유스케이스만 부른다")
    void inboundAdapters_goThroughUseCases() {
        check(noClasses()
                .that()
                .resideInAPackage("com.gole.api..adapter.in..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.gole.api..application.service..",
                        "com.gole.api..application.port.out..",
                        "com.gole.api..adapter.out.."));
    }

    @Test
    @DisplayName("인바운드 어댑터는 트랜잭션을 걸지 않는다 — 트랜잭션 경계는 유스케이스가 정한다")
    void inboundAdapters_doNotDemarcateTransactions() {
        check(noClasses()
                .that()
                .resideInAPackage("com.gole.api..adapter.in..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework.transaction.."));
    }

    @Test
    @DisplayName("common 은 어느 컨텍스트에도 의존하지 않는다")
    void common_dependsOnNoContext() {
        check(noClasses()
                .that()
                .resideInAPackage("com.gole.api.common..")
                .should()
                .dependOnClassesThat(insideAContext()));
    }

    private static void check(ArchRule rule) {
        rule.check(classes);
    }

    /** {@code com.gole.api.<컨텍스트>.…} 의 컨텍스트 이름. common·생성 코드·루트면 {@code null}. */
    private static String contextOf(JavaClass javaClass) {
        JavaClass base = javaClass.isArray() ? javaClass.getBaseComponentType() : javaClass;
        String pkg = base.getPackageName();
        if (!pkg.startsWith(ROOT) || pkg.endsWith(".grpc")) {
            return null;
        }
        String rest = pkg.substring(ROOT.length());
        String context = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
        return "common".equals(context) ? null : context;
    }

    /** 컨텍스트 안에서의 패키지 경로("adapter.out.listing" 등). */
    private static String pathWithinContext(JavaClass javaClass, String context) {
        JavaClass base = javaClass.isArray() ? javaClass.getBaseComponentType() : javaClass;
        String pkg = base.getPackageName();
        String prefix = ROOT + context;
        return pkg.length() > prefix.length() ? pkg.substring(prefix.length() + 1) : "";
    }

    private static DescribedPredicate<JavaClass> insideAContext() {
        return DescribedPredicate.describe("컨텍스트 안의 클래스", javaClass -> contextOf(javaClass) != null);
    }

    private static DescribedPredicate<JavaClass> implementInboundPort() {
        return DescribedPredicate.describe(
                "인바운드 포트를 구현하는 클래스",
                javaClass -> javaClass.getAllRawInterfaces().stream()
                        .anyMatch(type -> type.getPackageName().contains(".application.port.in")));
    }

    private static ArchCondition<JavaClass> dependOnlyOnOwnContext() {
        return new ArchCondition<>("자기 컨텍스트와 common 에만 의존한다") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String source = contextOf(item);
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    String target = contextOf(dependency.getTargetClass());
                    if (target != null && !target.equals(source)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> dependOnOtherContextsOnlyThroughInboundPorts() {
        return new ArchCondition<>("다른 컨텍스트에는 application.port.in 과 domain 으로만 의존한다") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String source = contextOf(item);
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass targetClass = dependency.getTargetClass();
                    String target = contextOf(targetClass);
                    if (target == null || target.equals(source)) {
                        continue;
                    }
                    String path = pathWithinContext(targetClass, target);
                    boolean allowed = path.equals("application.port.in")
                            || path.startsWith("application.port.in.")
                            || path.equals("domain")
                            || path.startsWith("domain.");
                    if (!allowed) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }
}
