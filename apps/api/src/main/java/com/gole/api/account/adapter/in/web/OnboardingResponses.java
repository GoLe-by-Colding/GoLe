package com.gole.api.account.adapter.in.web;

import com.gole.api.account.application.port.in.GetOnboardingStatusUseCase.OnboardingStatus;
import com.gole.api.account.application.port.in.RequestPhoneVerificationUseCase.PhoneVerificationRequested;
import java.util.List;

/**
 * 온보딩 응답 DTO 모음.
 */
public final class OnboardingResponses {

    private OnboardingResponses() {}

    /**
     * 재개용 진행 상태. (R2)
     *
     * <p>프론트는 {@code *Completed} 플래그로 이미 끝난 단계를 건너뛰고, {@code required}로
     * 강제 여부를, {@code legacyExempt}로 "배너만 노출" 여부를 판단한다.
     */
    public record OnboardingStatusResponse(
            boolean required,
            boolean legacyExempt,
            boolean nicknameCompleted,
            String nickname,
            boolean phoneCompleted,
            String maskedPhoneNumber,
            boolean interestTagsCompleted,
            List<String> interestTags,
            boolean privacyConsented,
            boolean marketingConsented) {

        public static OnboardingStatusResponse from(OnboardingStatus status) {
            return new OnboardingStatusResponse(
                    status.required(),
                    status.legacyExempt(),
                    status.nicknameCompleted(),
                    status.nickname(),
                    status.phoneCompleted(),
                    status.maskedPhoneNumber(),
                    status.interestTagsCompleted(),
                    status.interestTags(),
                    status.privacyConsented(),
                    status.marketingConsented());
        }
    }

    public record InterestTagsResponse(List<String> tags) {}

    /** 코드 자체는 절대 응답에 담지 않는다 — 어디로 보냈는지와 유효 시간만 돌려준다. */
    public record PhoneVerificationResponse(String maskedPhoneNumber, long expiresInSeconds) {

        public static PhoneVerificationResponse from(PhoneVerificationRequested requested) {
            return new PhoneVerificationResponse(requested.maskedPhoneNumber(), requested.expiresInSeconds());
        }
    }
}
