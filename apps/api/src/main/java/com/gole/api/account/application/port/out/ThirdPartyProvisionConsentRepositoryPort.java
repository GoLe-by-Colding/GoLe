package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent;
import java.util.Optional;

/** Outbound port: 제3자 제공 동의 이력을 append-only로 저장하고 현재 결정을 읽는다. */
public interface ThirdPartyProvisionConsentRepositoryPort {

    /** 같은 계정·requestId 재시도면 기존 이벤트를 반환하고, 그 외에는 새 이벤트를 삽입한다. */
    ThirdPartyProvisionConsentEvent appendOnce(ThirdPartyProvisionConsentEvent event);

    Optional<ThirdPartyProvisionConsentEvent> findLatest(String accountId, String noticeVersion);
}
