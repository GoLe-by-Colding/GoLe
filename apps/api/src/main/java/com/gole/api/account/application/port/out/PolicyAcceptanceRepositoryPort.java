package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.PolicyAcceptance;

/** Outbound port: 정책 확인 증빙은 같은 버전에 대해 한 번만 추가한다. */
public interface PolicyAcceptanceRepositoryPort {

    void appendOnce(PolicyAcceptance acceptance);
}
