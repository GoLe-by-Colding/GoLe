package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.in.GetOnboardingStatusUseCase;
import com.gole.api.account.application.port.in.GetOnboardingStatusUseCase.OnboardingStatus;
import com.gole.api.account.domain.exception.SellerIdentityVerificationRequiredException;
import com.gole.api.common.config.SellerIdentityVerificationProperties;
import com.gole.api.common.exception.ServiceUnavailableException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SellerIdentityVerificationServiceTest {

    private final GetOnboardingStatusUseCase onboardingStatus = mock(GetOnboardingStatusUseCase.class);
    private final SellerIdentityVerificationProperties properties = new SellerIdentityVerificationProperties();
    private final SellerIdentityVerificationService service =
            new SellerIdentityVerificationService(onboardingStatus, properties);

    @Test
    void missingRuntimeReadinessFailsClosedBeforeReadingAnAccount() {
        assertThatThrownBy(() -> service.requireVerifiedSeller("seller-1"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", "SELLER_IDENTITY_VERIFICATION_UNAVAILABLE");

        verify(onboardingStatus, never()).status(anyString());
    }

    @Test
    void verifiedPhoneIsRequiredEvenForLegacyExemptAccount() {
        properties.setVerificationReady(true);
        when(onboardingStatus.status("legacy-seller")).thenReturn(status(false, true));

        assertThatThrownBy(() -> service.requireVerifiedSeller("legacy-seller"))
                .isInstanceOf(SellerIdentityVerificationRequiredException.class)
                .hasFieldOrPropertyWithValue("code", "SELLER_IDENTITY_VERIFICATION_REQUIRED");
    }

    @Test
    void runtimeReadyAndVerifiedPhonePass() {
        properties.setVerificationReady(true);
        when(onboardingStatus.status("seller-1")).thenReturn(status(true, false));

        assertThatCode(() -> service.requireVerifiedSeller("seller-1")).doesNotThrowAnyException();
    }

    private static OnboardingStatus status(boolean phoneCompleted, boolean legacyExempt) {
        return new OnboardingStatus(
                "seller-1",
                true,
                "판매자",
                false,
                phoneCompleted,
                phoneCompleted ? "010-****-0001" : null,
                true,
                List.of("technic"),
                true,
                false,
                false,
                legacyExempt);
    }
}
