package com.gole.api.notification.application.port.out;

import java.util.List;
import java.util.Optional;

/** notification 컨텍스트가 계정 도메인 타입 없이 관심태그 수신자를 조회하는 포트. */
public interface InterestTagRecipientPort {

    List<String> listEligibleAccountIds(String tagKey, String afterAccountId, int limit);

    Optional<Recipient> resolveEligible(String accountId, String tagKey);

    record Recipient(String accountId, String phoneNumber) {}
}
