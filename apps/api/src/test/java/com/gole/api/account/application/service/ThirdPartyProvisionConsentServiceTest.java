package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.account.application.port.out.ThirdPartyProvisionConsentRepositoryPort;
import com.gole.api.account.domain.model.PolicyAcceptance.Channel;
import com.gole.api.account.domain.model.SignupPolicyAcceptance;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent.Decision;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent.SourcePath;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThirdPartyProvisionConsentServiceTest {

    private final Instant now = Instant.parse("2026-09-04T01:02:03Z");
    private InMemoryConsentEvents events;
    private ThirdPartyProvisionConsentService service;

    @BeforeEach
    void setUp() {
        events = new InMemoryConsentEvents();
        service = new ThirdPartyProvisionConsentService(
                events, new SignupPolicyProperties(), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void currentVersionRequiresConsentAndSameRequestIsIdempotent() {
        assertThat(service.currentStatus("account-1"))
                .extracting("noticeVersion", "consented", "lastDecisionAt")
                .containsExactly("2026-09-04", false, null);
        assertThatThrownBy(() -> service.requireCurrent("account-1"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo(ThirdPartyProvisionConsentService.REQUIRED_CODE);

        service.consent("account-1", "2026-09-04", SourcePath.LISTING_CHAT, "request-0001");
        service.consent("account-1", "2026-09-04", SourcePath.LISTING_CHAT, "request-0001");

        assertThat(events.all).singleElement().satisfies(event -> {
            assertThat(event.accountId()).isEqualTo("account-1");
            assertThat(event.noticeVersion()).isEqualTo("2026-09-04");
            assertThat(event.decision()).isEqualTo(Decision.CONSENTED);
            assertThat(event.sourcePath()).isEqualTo(SourcePath.LISTING_CHAT);
            assertThat(event.occurredAt()).isEqualTo(now);
        });
        assertThat(service.currentStatus("account-1").consented()).isTrue();
    }

    @Test
    void withdrawalBlocksNewProvisionAndLaterConsentRestoresItWithoutDeletingHistory() {
        service.consent("account-1", "2026-09-04", SourcePath.SOCIAL_DIRECT_CHAT, "request-0001");
        service.withdraw("account-1", "2026-09-04", "request-0002");

        assertThat(service.currentStatus("account-1").consented()).isFalse();
        assertThatThrownBy(() -> service.requireCurrent("account-1")).isInstanceOf(ForbiddenException.class);

        service.consent("account-1", "2026-09-04", SourcePath.CHAT_MESSAGE, "request-0003");

        assertThat(service.currentStatus("account-1").consented()).isTrue();
        assertThat(events.all)
                .extracting(ThirdPartyProvisionConsentEvent::decision)
                .containsExactly(Decision.CONSENTED, Decision.WITHDRAWN, Decision.CONSENTED);
    }

    @Test
    void staleNoticeAndRequestIdReuseForAnotherDecisionFailClosed() {
        assertThatThrownBy(() -> service.consent("account-1", "old", SourcePath.ORDER_CONTACTS, "request-0001"))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo("THIRD_PARTY_PROVISION_VERSION_STALE");

        service.consent("account-1", "2026-09-04", SourcePath.ORDER_CONTACTS, "request-0001");
        assertThatThrownBy(() -> service.withdraw("account-1", "2026-09-04", "request-0001"))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("CONSENT_REQUEST_ID_REUSED");
        assertThat(events.all).hasSize(1);
    }

    @Test
    void replayingAnOldIdempotencyKeyReturnsTheActualLatestDecision() {
        var first = service.consent("account-1", "2026-09-04", SourcePath.ORDER_CONTACTS, "request-0001");
        assertThat(first.consented()).isTrue();
        service.withdraw("account-1", "2026-09-04", "request-0002");

        var replay = service.consent("account-1", "2026-09-04", SourcePath.ORDER_CONTACTS, "request-0001");

        assertThat(replay.consented()).isFalse();
        assertThat(events.all).hasSize(2);
    }

    @Test
    void signupChoiceIsOptionalButCheckedChoiceRecordsProviderSpecificEvidence() {
        var declined = new SignupPolicyAcceptance("2026-09-04", "2026-09-05", true, true, true, "2026-09-04", false);
        service.validateSignupChoice(declined);
        service.recordSignupIfAccepted("account-1", declined, Channel.EMAIL);
        assertThat(events.all).isEmpty();

        var accepted = new SignupPolicyAcceptance("2026-09-04", "2026-09-05", true, true, true, "2026-09-04", true);
        service.validateSignupChoice(accepted);
        service.recordSignupIfAccepted("account-2", accepted, Channel.SOCIAL_GOOGLE);
        service.recordSignupIfAccepted("account-2", accepted, Channel.SOCIAL_GOOGLE);

        assertThat(events.all).singleElement().satisfies(event -> {
            assertThat(event.sourcePath()).isEqualTo(SourcePath.SOCIAL_GOOGLE_SIGNUP);
            assertThat(event.requestId()).contains("account-2", "2026-09-04");
        });
    }

    @Test
    void checkedSignupChoiceCannotOmitNoticeVersion() {
        var input = new SignupPolicyAcceptance("2026-09-04", "2026-09-05", true, true, true, null, true);

        assertThatThrownBy(() -> service.validateSignupChoice(input))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo("THIRD_PARTY_PROVISION_VERSION_REQUIRED");
    }

    @Test
    void declinedSignupChoiceDoesNotDependOnTheOptionalNoticeVersion() {
        var declinedWithStaleVersion =
                new SignupPolicyAcceptance("2026-09-04", "2026-09-05", true, true, true, "old-version", false);

        service.validateSignupChoice(declinedWithStaleVersion);
        service.recordSignupIfAccepted("account-1", declinedWithStaleVersion, Channel.EMAIL);

        assertThat(events.all).isEmpty();
    }

    private static final class InMemoryConsentEvents implements ThirdPartyProvisionConsentRepositoryPort {

        private final List<ThirdPartyProvisionConsentEvent> all = new ArrayList<>();
        private final Map<String, ThirdPartyProvisionConsentEvent> byAccountAndRequest = new LinkedHashMap<>();

        @Override
        public ThirdPartyProvisionConsentEvent appendOnce(ThirdPartyProvisionConsentEvent event) {
            String key = event.accountId() + "\0" + event.requestId();
            ThirdPartyProvisionConsentEvent existing = byAccountAndRequest.get(key);
            if (existing != null) {
                return existing;
            }
            byAccountAndRequest.put(key, event);
            all.add(event);
            return event;
        }

        @Override
        public Optional<ThirdPartyProvisionConsentEvent> findLatest(String accountId, String noticeVersion) {
            for (int index = all.size() - 1; index >= 0; index--) {
                ThirdPartyProvisionConsentEvent event = all.get(index);
                if (event.accountId().equals(accountId) && event.noticeVersion().equals(noticeVersion)) {
                    return Optional.of(event);
                }
            }
            return Optional.empty();
        }
    }
}
