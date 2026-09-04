package com.gole.api.account.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 온보딩으로 채워지는 계정 프로필. (onboarding R1)
 *
 * <p><b>완료 플래그를 저장하지 않는다</b>(D1). 완료 여부는 아래 필드 조합에서 파생한다 —
 * {@code tradeMode}가 launch 단계에서 파생되고 저장되지 않는 것과 같은 원칙이다. 플래그를
 * 따로 두면 필드를 지운 계정이 "완료됨"으로 남는 어긋남이 반드시 생긴다.
 *
 * <p>{@code legacyExempt}만은 파생값이 아니라 마이그레이션 시점에 저장하는 사실이다(D6).
 * 배포 이전 가입자가 자발적으로 일부 단계를 마쳐도 이 값은 바뀌지 않는다.
 */
public record OnboardingProfile(
        Nickname nickname,
        PhoneNumber phoneNumber,
        Instant phoneVerifiedAt,
        Set<String> interestTags,
        Instant privacyConsentedAt,
        Instant marketingConsentedAt,
        boolean legacyExempt) {

    public OnboardingProfile {
        interestTags = interestTags == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(interestTags));
    }

    /** 아직 아무 단계도 밟지 않은 신규 계정의 프로필. */
    public static OnboardingProfile empty() {
        return new OnboardingProfile(null, null, null, Set.of(), null, null, false);
    }

    /**
     * 운영 시드/부트스트랩 계정(관리자 등)의 프로필. 소비자 가입 절차를 밟지 않으므로
     * 애초에 온보딩 대상이 아니다 — legacyExempt를 배포 시점 마이그레이션과 같은 의미로
     * 처음부터 true로 둔다.
     */
    public static OnboardingProfile exempt() {
        return new OnboardingProfile(null, null, null, Set.of(), null, null, true);
    }

    public boolean hasNickname() {
        return nickname != null;
    }

    /** 번호만 입력하고 코드를 맞히지 못한 상태와 구분한다 — 인증 시각이 유일한 근거다. */
    public boolean isPhoneVerified() {
        return phoneNumber != null && phoneVerifiedAt != null;
    }

    public boolean hasInterestTags() {
        return !interestTags.isEmpty();
    }

    /** 개인정보 수집·이용 동의(필수). 마케팅 동의는 선택이므로 완료 판정에 넣지 않는다. */
    public boolean hasPrivacyConsent() {
        return privacyConsentedAt != null;
    }

    /** 네 단계를 모두 마쳤는가. (D1의 파생 식) */
    public boolean isComplete() {
        return hasNickname() && isPhoneVerified() && hasInterestTags() && hasPrivacyConsent();
    }

    /**
     * 온보딩을 더 요구해야 하는가.
     *
     * <p>{@code legacyExempt} 계정은 미완료여도 항상 {@code false} — 배포 이전 가입자를
     * 하드 게이트에 걸지 않는다는 D6의 약속이 여기 한 줄로 지켜진다.
     */
    public boolean isRequired() {
        return !legacyExempt && !isComplete();
    }

    public OnboardingProfile withNickname(Nickname newNickname) {
        return new OnboardingProfile(
                newNickname,
                phoneNumber,
                phoneVerifiedAt,
                interestTags,
                privacyConsentedAt,
                marketingConsentedAt,
                legacyExempt);
    }

    public OnboardingProfile withVerifiedPhone(PhoneNumber verified, Instant verifiedAt) {
        return new OnboardingProfile(
                nickname, verified, verifiedAt, interestTags, privacyConsentedAt, marketingConsentedAt, legacyExempt);
    }

    public OnboardingProfile withInterestTags(Set<String> tags) {
        return new OnboardingProfile(
                nickname, phoneNumber, phoneVerifiedAt, tags, privacyConsentedAt, marketingConsentedAt, legacyExempt);
    }

    public OnboardingProfile withConsents(Instant privacyAt, Instant marketingAt) {
        return new OnboardingProfile(
                nickname, phoneNumber, phoneVerifiedAt, interestTags, privacyAt, marketingAt, legacyExempt);
    }

    public OnboardingProfile asLegacyExempt() {
        return new OnboardingProfile(
                nickname, phoneNumber, phoneVerifiedAt, interestTags, privacyConsentedAt, marketingConsentedAt, true);
    }
}
