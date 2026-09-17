package com.gole.api.notification.adapter.out.account;

import com.gole.api.account.application.port.in.ListInterestTagRecipientsUseCase;
import com.gole.api.notification.application.port.out.InterestTagRecipientPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** account 인바운드 포트를 notification 수신자 포트로 변환한다. */
@Component
public class AccountInterestTagRecipientAdapter implements InterestTagRecipientPort {

    private final ListInterestTagRecipientsUseCase recipients;

    public AccountInterestTagRecipientAdapter(ListInterestTagRecipientsUseCase recipients) {
        this.recipients = recipients;
    }

    @Override
    public List<String> listEligibleAccountIds(String tagKey, String afterAccountId, int limit) {
        return recipients.listEligibleAccountIds(tagKey, afterAccountId, limit);
    }

    @Override
    public Optional<Recipient> resolveEligible(String accountId, String tagKey) {
        return recipients
                .resolveEligible(accountId, tagKey)
                .map(recipient -> new Recipient(
                        recipient.accountId(), recipient.phoneNumber().value()));
    }
}
