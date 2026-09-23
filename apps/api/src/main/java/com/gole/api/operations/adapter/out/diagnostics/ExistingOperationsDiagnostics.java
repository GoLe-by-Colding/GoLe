package com.gole.api.operations.adapter.out.diagnostics;

import com.gole.api.admin.application.service.ExceptionQueueService;
import com.gole.api.common.operations.DiscordOperationsProperties;
import com.gole.api.operations.application.port.out.OperationsDiagnostics;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ExistingOperationsDiagnostics implements OperationsDiagnostics {
    private final ExceptionQueueService exceptions;
    private final GetPaymentReadinessUseCase payment;
    private final DiscordOperationsProperties discord;
    private final Environment environment;

    public ExistingOperationsDiagnostics(
            ExceptionQueueService exceptions,
            GetPaymentReadinessUseCase payment,
            DiscordOperationsProperties discord,
            Environment environment) {
        this.exceptions = exceptions;
        this.payment = payment;
        this.discord = discord;
        this.environment = environment;
    }

    public String inspect(String jobId) {
        return switch (jobId) {
            case "exception-queue" -> "EXCEPTIONS_" + exceptions.list().size();
            case "payment-readiness" ->
                "PAYMENT_" + payment.getPaymentReadiness().state().name();
            case "alert-readiness" ->
                (discord.isEnabled() ? "DISCORD_CONFIGURED_" : "DISCORD_DISABLED_") + sentryConfiguration();
            default -> throw new IllegalArgumentException("UNKNOWN_OPERATION");
        };
    }
    /** API 수집/웹 조회 설정만 읽는다. SDK·외부 서비스에 요청하지 않는다. */
    private String sentryConfiguration() {
        boolean collect = environment.getProperty("gole.sentry.enabled", Boolean.class, false);
        boolean poll = environment.getProperty("gole.sentry.poll-enabled", Boolean.class, false);
        if (!collect && !poll) return "SENTRY_DISABLED";
        String dsn = environment.getProperty("gole.sentry.dsn", "");
        boolean configured = "production".equals(environment.getProperty("gole.sentry.environment"))
                && (!collect || dsn.matches("https://[A-Za-z0-9]{1,64}@[A-Za-z0-9.-]+\\.sentry\\.io/[0-9]+"))
                && (!poll
                        || !environment
                                .getProperty("gole.sentry.read-token", "")
                                .isBlank());
        return configured ? "SENTRY_CONFIGURED_UNVERIFIED" : "SENTRY_MISCONFIGURED";
    }
}
