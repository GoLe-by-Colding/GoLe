package com.gole.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.account.adapter.out.persistence.ThirdPartyProvisionConsentMongoRepository;
import com.gole.api.account.application.port.out.ThirdPartyProvisionConsentRepositoryPort;
import com.gole.api.account.application.service.ThirdPartyProvisionConsentService;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent.Decision;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent.SourcePath;
import com.gole.api.common.exception.ConflictException;
import java.time.Instant;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 제3자 제공 동의 이력의 append-only·멱등·최신 결정 계약을 실제 Mongo 인덱스로 검증한다. */
@SpringBootTest
@Testcontainers
class ThirdPartyProvisionConsentPersistenceIntegrationTest {

    private static final String NOTICE_VERSION = "2026-09-04";

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("gole.policy.third-party-provision-version", () -> NOTICE_VERSION);
        registry.add("gole.catalog.seed-on-empty", () -> "false");
        registry.add("gole.listing.seed-on-empty", () -> "false");
        registry.add("gole.pricing.seed-on-empty", () -> "false");
        registry.add("gole.community.seed-on-empty", () -> "false");
        registry.add("gole.report.seed-on-empty", () -> "false");
        registry.add("gole.review.seed-on-empty", () -> "false");
        registry.add("gole.media.seed-on-startup", () -> "false");
    }

    @Autowired
    ThirdPartyProvisionConsentService consents;

    @Autowired
    ThirdPartyProvisionConsentRepositoryPort events;

    @Autowired
    ThirdPartyProvisionConsentMongoRepository documents;

    @BeforeEach
    void clean() {
        documents.deleteAll();
    }

    @Test
    void repeatedRequestIsIdempotentAndCannotBeReusedForAnotherDecision() {
        var first = consents.consent("account-consent", NOTICE_VERSION, SourcePath.CHAT_MESSAGE, "request-1");
        var replay = consents.consent("account-consent", NOTICE_VERSION, SourcePath.CHAT_MESSAGE, "request-1");

        assertThat(replay).isEqualTo(first);
        assertThat(documents.count()).isEqualTo(1);
        assertThatThrownBy(() -> consents.withdraw("account-consent", NOTICE_VERSION, "request-1"))
                .isInstanceOf(ConflictException.class);
        assertThat(documents.count()).isEqualTo(1);
        assertThat(consents.currentStatus("account-consent").consented()).isTrue();
    }

    @Test
    void latestDecisionUsesObjectIdAsTieBreakerWithoutOverwritingHistory() {
        Instant sameMillisecond = Instant.parse("2026-09-04T00:00:00Z");
        String accountId = "account-ordering";
        events.appendOnce(event(accountId, "request-consent", Decision.CONSENTED, sameMillisecond));
        events.appendOnce(event(accountId, "request-withdraw", Decision.WITHDRAWN, sameMillisecond));
        events.appendOnce(event(accountId, "request-reconsent", Decision.CONSENTED, sameMillisecond));

        assertThat(events.findLatest(accountId, NOTICE_VERSION))
                .get()
                .extracting(ThirdPartyProvisionConsentEvent::decision)
                .isEqualTo(Decision.CONSENTED);
        assertThat(documents.count()).isEqualTo(3);
    }

    private static ThirdPartyProvisionConsentEvent event(
            String accountId, String requestId, Decision decision, Instant occurredAt) {
        return new ThirdPartyProvisionConsentEvent(
                new ObjectId().toHexString(),
                accountId,
                NOTICE_VERSION,
                decision,
                SourcePath.ACCOUNT_SETTINGS,
                requestId,
                occurredAt);
    }
}
