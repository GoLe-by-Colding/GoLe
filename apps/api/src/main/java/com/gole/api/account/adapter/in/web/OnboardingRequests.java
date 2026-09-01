package com.gole.api.account.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * 온보딩 요청 DTO 모음. Bean Validation으로 1차 형식 검증만 하고, 실제 규칙(D9 문자 구성,
 * D8 목록 대조 등)은 도메인이 다시 본다 — 화면을 우회한 호출도 같은 규칙에 걸려야 한다.
 */
public final class OnboardingRequests {

    private OnboardingRequests() {}

    public record SetNicknameRequest(@NotBlank @Size(min = 2, max = 12) String nickname) {}

    public record RequestPhoneVerificationRequest(@NotBlank @Size(max = 20) String phoneNumber) {}

    public record ConfirmPhoneVerificationRequest(@NotBlank @Pattern(regexp = "\\d{6}") String code) {}

    public record SelectInterestTagsRequest(@NotEmpty @Size(max = 5) Set<@NotBlank @Size(max = 30) String> tags) {}

    /** {@code privacyAgreed=false}는 400이 아니라 도메인에서 거부한다(R7). */
    public record SubmitConsentRequest(boolean privacyAgreed, boolean marketingAgreed) {}
}
