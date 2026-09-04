package com.gole.api.account.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** 제3자 제공 동의·철회 이력. 업데이트 경로가 없는 append-only 감사 문서다. */
@Document(collection = "third_party_provision_consent_events")
@CompoundIndexes({
    @CompoundIndex(
            name = "account_consent_request_unique_idx",
            def = "{'accountId': 1, 'requestId': 1}",
            unique = true),
    @CompoundIndex(
            name = "account_notice_latest_idx",
            def = "{'accountId': 1, 'noticeVersion': 1, 'occurredAt': -1, '_id': -1}")
})
public class ThirdPartyProvisionConsentDocument {

    @Id
    private String id;

    @Indexed
    private String accountId;

    private String noticeVersion;
    private String decision;
    private String sourcePath;
    private String requestId;
    private Instant occurredAt;

    protected ThirdPartyProvisionConsentDocument() {}

    public ThirdPartyProvisionConsentDocument(
            String id,
            String accountId,
            String noticeVersion,
            String decision,
            String sourcePath,
            String requestId,
            Instant occurredAt) {
        this.id = id;
        this.accountId = accountId;
        this.noticeVersion = noticeVersion;
        this.decision = decision;
        this.sourcePath = sourcePath;
        this.requestId = requestId;
        this.occurredAt = occurredAt;
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getNoticeVersion() {
        return noticeVersion;
    }

    public String getDecision() {
        return decision;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
