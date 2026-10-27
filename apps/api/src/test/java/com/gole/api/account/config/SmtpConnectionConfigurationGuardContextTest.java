package com.gole.api.account.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * {@link SmtpConnectionConfigurationGuardTest}는 패키지 전용 테스트 생성자를 직접 호출하므로
 * Spring의 생성자 선택이나 운영 생성자의 의존성 주입 오류를 검증하지 못한다.
 * 이 테스트는 Spring Boot 기동과 같은 방식으로 최소한의 실제 애플리케이션 컨텍스트를 띄운다.
 */
class SmtpConnectionConfigurationGuardContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SmtpConnectionConfigurationGuard.class)
            .withBean(JavaMailSenderImpl.class, JavaMailSenderImpl::new);

    @Test
    void disabledByDefaultDoesNotRegisterGuard() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(SmtpConnectionConfigurationGuard.class));
    }

    @Test
    void enabledFlagBootsContextAndRegistersGuardWithoutInvokingTheVerifier() {
        // gole.verification.email.enabled=true일 때만 컴포넌트가 등록되므로
        // 생성자 주입 오류는 이 설정을 켜야 드러난다.
        // ApplicationContextRunner는 SpringApplication.run()을 호출하지 않아
        // ApplicationRunner 빈도 실행하지 않는다. JavaMailSenderImpl에는 호스트가 없어
        // 실제 접속을 시도하면 즉시 실패한다. 그런데도 이 테스트가 통과하므로
        // 컨텍스트 갱신만으로 검증기의 run()이 실행되지 않음을 확인할 수 있다.
        contextRunner.withPropertyValues("gole.verification.email.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SmtpConnectionConfigurationGuard.class);
        });
    }
}
