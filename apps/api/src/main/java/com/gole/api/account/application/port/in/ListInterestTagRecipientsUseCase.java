package com.gole.api.account.application.port.in;

import com.gole.api.account.domain.model.PhoneNumber;
import java.util.List;
import java.util.Optional;

/** 관심태그 알림톡 수신 후보 조회와 발송 직전 자격 재검증을 제공한다. */
public interface ListInterestTagRecipientsUseCase {

    List<String> listEligibleAccountIds(String tagKey, String afterAccountId, int limit);

    Optional<MarketingRecipient> resolveEligible(String accountId, String tagKey);

    record MarketingRecipient(String accountId, PhoneNumber phoneNumber) {}
}
