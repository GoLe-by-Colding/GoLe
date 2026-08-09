package com.gole.api.order.application.port.in;

import java.util.List;

/** 관리자 운영 화면에서 비밀값 없이 결제 연동 준비 상태를 조회한다. */
public interface GetPaymentReadinessUseCase {

    Snapshot getPaymentReadiness();

    enum State {
        DISABLED,
        MISCONFIGURED,
        READY
    }

    enum ChannelType {
        TEST,
        LIVE,
        UNKNOWN
    }

    enum Problem {
        MISSING,
        INVALID
    }

    record ConfigurationIssue(String setting, Problem problem) {}

    record Snapshot(
            boolean enabled,
            boolean ready,
            State state,
            ChannelType channelType,
            String provider,
            String currency,
            List<ConfigurationIssue> issues) {}
}
