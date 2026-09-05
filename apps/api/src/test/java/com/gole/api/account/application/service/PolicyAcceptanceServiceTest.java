package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gole.api.account.domain.model.PolicyAcceptance;
import com.gole.api.account.domain.model.SignupPolicyAcceptance;
import com.gole.api.common.exception.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyAcceptanceServiceTest {

    private List<PolicyAcceptance> recorded;
    private PolicyAcceptanceService service;
    private ThirdPartyProvisionConsentService thirdPartyProvisionConsents;

    @BeforeEach
    void setUp() {
        recorded = new ArrayList<>();
        thirdPartyProvisionConsents = mock(ThirdPartyProvisionConsentService.class);
        service = new PolicyAcceptanceService(
                recorded::add,
                new SignupPolicyProperties(),
                Clock.fixed(Instant.parse("2026-09-03T10:15:30Z"), ZoneOffset.UTC),
                thirdPartyProvisionConsents);
    }

    @Test
    void currentPolicyAndRecordedEvidenceUseSameConfiguredVersions() {
        var current = service.currentSignupPolicy();
        var input = new SignupPolicyAcceptance(current.termsVersion(), current.privacyVersion(), true, true, true);

        service.record("account-1", input, PolicyAcceptance.Channel.EMAIL);

        assertThat(recorded).singleElement().satisfies(evidence -> {
            assertThat(evidence.accountId()).isEqualTo("account-1");
            assertThat(evidence.termsVersion()).isEqualTo(current.termsVersion());
            assertThat(evidence.acceptedAt()).isEqualTo("2026-09-03T10:15:30Z");
        });
        assertThat(current.thirdPartyProvisionVersion()).isEqualTo("2026-09-04");
    }

    @Test
    void rejectsMissingConfirmationAndStaleVersion() {
        assertThatThrownBy(() ->
                        service.validate(new SignupPolicyAcceptance("2026-09-04", "2026-09-05", true, false, true)))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo("POLICY_ACCEPTANCE_REQUIRED");

        assertThatThrownBy(() -> service.validate(new SignupPolicyAcceptance("2026-09-04", "old", true, true, true)))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo("POLICY_VERSION_STALE");
    }

    @Test
    void optionalThirdPartyChoiceIsValidatedAndRecordedSeparately() {
        var input = new SignupPolicyAcceptance("2026-09-04", "2026-09-05", true, true, true, "2026-09-04", true);

        service.record("account-1", input, PolicyAcceptance.Channel.EMAIL);

        verify(thirdPartyProvisionConsents).validateSignupChoice(input);
        verify(thirdPartyProvisionConsents).recordSignupIfAccepted("account-1", input, PolicyAcceptance.Channel.EMAIL);
    }
}
