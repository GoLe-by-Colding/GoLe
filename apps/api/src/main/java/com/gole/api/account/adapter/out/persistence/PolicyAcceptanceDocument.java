package com.gole.api.account.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** 가입 정책 확인 이력. 업데이트 API를 두지 않는 append-only 감사 자료다. */
@Document(collection = "policy_acceptances")
@CompoundIndex(
        name = "account_policy_versions_unique_idx",
        def = "{'accountId': 1, 'termsVersion': 1, 'privacyVersion': 1}",
        unique = true)
public class PolicyAcceptanceDocument {

    @Id
    private String id;

    @Indexed
    private String accountId;

    private String termsVersion;
    private String privacyVersion;
    private boolean termsAccepted;
    private boolean privacyAcknowledged;
    private boolean minimumAgeConfirmed;
    private String channel;

    @Indexed
    private Instant acceptedAt;

    protected PolicyAcceptanceDocument() {}

    public PolicyAcceptanceDocument(
            String id,
            String accountId,
            String termsVersion,
            String privacyVersion,
            boolean termsAccepted,
            boolean privacyAcknowledged,
            boolean minimumAgeConfirmed,
            String channel,
            Instant acceptedAt) {
        this.id = id;
        this.accountId = accountId;
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
        this.termsAccepted = termsAccepted;
        this.privacyAcknowledged = privacyAcknowledged;
        this.minimumAgeConfirmed = minimumAgeConfirmed;
        this.channel = channel;
        this.acceptedAt = acceptedAt;
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public String getPrivacyVersion() {
        return privacyVersion;
    }

    public boolean isTermsAccepted() {
        return termsAccepted;
    }

    public boolean isPrivacyAcknowledged() {
        return privacyAcknowledged;
    }

    public boolean isMinimumAgeConfirmed() {
        return minimumAgeConfirmed;
    }

    public String getChannel() {
        return channel;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
