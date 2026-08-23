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

    /**
     * @param methods 지금 열려 있는 결제수단 식별자(예: {@code ["KAKAOPAY", "CARD"]}). 결제수단이
     *     설정에 따라 늘고 주는 값이므로 고정 문자열 하나로는 운영 상태를 나타낼 수 없다.
     */
    record Snapshot(
            boolean enabled,
            boolean ready,
            State state,
            ChannelType channelType,
            List<String> methods,
            String currency,
            List<ConfigurationIssue> issues) {}
}
