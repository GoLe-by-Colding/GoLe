package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.GetCurrentSignupPolicyUseCase;
import com.gole.api.account.application.port.out.PolicyAcceptanceRepositoryPort;
import com.gole.api.account.domain.model.PolicyAcceptance;
import com.gole.api.account.domain.model.PolicyAcceptance.Channel;
import com.gole.api.account.domain.model.SignupPolicyAcceptance;
import com.gole.api.common.exception.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 현재 정책을 검증하고 가입 증빙을 추가하는 단일 진입점. */
@Service
public class PolicyAcceptanceService implements GetCurrentSignupPolicyUseCase {

    private final PolicyAcceptanceRepositoryPort repository;
    private final SignupPolicyProperties properties;
    private final Clock clock;
    private final ThirdPartyProvisionConsentService thirdPartyProvisionConsents;

    public PolicyAcceptanceService(
            PolicyAcceptanceRepositoryPort repository,
            SignupPolicyProperties properties,
            Clock clock,
            ThirdPartyProvisionConsentService thirdPartyProvisionConsents) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.thirdPartyProvisionConsents = thirdPartyProvisionConsents;
    }

    @Override
    public CurrentSignupPolicy currentSignupPolicy() {
        return new CurrentSignupPolicy(
                properties.getTermsVersion(),
                properties.getPrivacyVersion(),
                properties.getThirdPartyProvisionVersion(),
                properties.getMinimumAge());
    }

    public void record(String accountId, SignupPolicyAcceptance input, Channel channel) {
        validate(input);
        repository.appendOnce(new PolicyAcceptance(
                UUID.randomUUID().toString(),
                accountId,
                input.termsVersion(),
                input.privacyVersion(),
                input.termsAccepted(),
                input.privacyAcknowledged(),
                input.minimumAgeConfirmed(),
                channel,
                Instant.now(clock)));
        thirdPartyProvisionConsents.recordSignupIfAccepted(accountId, input, channel);
    }

    public void validate(SignupPolicyAcceptance input) {
        if (input == null || !input.termsAccepted() || !input.privacyAcknowledged() || !input.minimumAgeConfirmed()) {
            throw new BadRequestException("POLICY_ACCEPTANCE_REQUIRED", "이용약관 확인, 개인정보처리방침 확인, 만 14세 이상 확인이 모두 필요합니다");
        }
        if (!properties.getTermsVersion().equals(input.termsVersion())
                || !properties.getPrivacyVersion().equals(input.privacyVersion())) {
            throw new BadRequestException("POLICY_VERSION_STALE", "정책이 변경되었습니다. 최신 내용을 확인한 뒤 다시 시도해 주세요");
        }
        thirdPartyProvisionConsents.validateSignupChoice(input);
    }
}
