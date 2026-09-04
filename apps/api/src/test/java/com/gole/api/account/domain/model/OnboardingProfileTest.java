package com.gole.api.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.BadRequestException;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 온보딩 완료 여부를 <b>파생</b>시키는 규칙 검증. (onboarding D1, R1)
 *
 * <p>완료 플래그를 저장하지 않기로 한 결정이 실제로 지켜지는지는 결국 이 표에 달려 있다.
 */
class OnboardingProfileTest {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void emptyProfileHasCompletedNoStep() {
        OnboardingProfile profile = OnboardingProfile.empty();

        assertThat(profile.hasNickname()).isFalse();
        assertThat(profile.isPhoneVerified()).isFalse();
        assertThat(profile.hasInterestTags()).isFalse();
        assertThat(profile.hasPrivacyConsent()).isFalse();
        assertThat(profile.isComplete()).isFalse();
        assertThat(profile.isRequired()).isTrue();
    }

    @Test
    void allFourStepsDoneStopsRequiringOnboarding() {
        OnboardingProfile profile = complete();

        assertThat(profile.isComplete()).isTrue();
        assertThat(profile.isRequired()).isFalse();
    }

    @Test
    void marketingConsentIsOptionalAndDoesNotAffectCompletion() {
        OnboardingProfile profile = complete();

        assertThat(profile.marketingConsentedAt()).isNull();
        assertThat(profile.isComplete()).isTrue();
    }

    @Test
    void anyMissingStepLeavesProfileIncomplete() {
        assertThat(complete().withNickname(null).isComplete()).isFalse();
        assertThat(complete().withInterestTags(Set.of()).isComplete()).isFalse();
        assertThat(complete().withConsents(null, null).isComplete()).isFalse();
    }

    @Test
    void phoneEnteredButNotVerifiedIsIncomplete() {
        // phoneVerifiedAt이 유일한 근거다 — 번호 존재만으로 인증됐다고 세면
        // 남의 번호를 적어 넣는 것만으로 게이트를 통과할 수 있다.
        OnboardingProfile profile = new OnboardingProfile(
                new Nickname("고레"), new PhoneNumber("01012345678"), null, Set.of("technic"), NOW, null, false);

        assertThat(profile.isPhoneVerified()).isFalse();
        assertThat(profile.isRequired()).isTrue();
    }

    @Test
    void phoneCanBeExcludedByTheDeploymentPolicyWithoutPersistingACompletionFlag() {
        OnboardingProfile profile = OnboardingProfile.empty()
                .withNickname(new Nickname("고레"))
                .withInterestTags(Set.of("technic"))
                .withConsents(NOW, null);

        assertThat(profile.isComplete()).isFalse();
        assertThat(profile.isComplete(false)).isTrue();
        assertThat(profile.isRequired(false)).isFalse();
    }

    @Test
    void legacyExemptAccountIsNeverRequiredEvenWhenIncomplete() {
        // D6: 배포 이전 가입자를 하드 게이트에 걸지 않는다.
        OnboardingProfile profile = OnboardingProfile.empty().asLegacyExempt();

        assertThat(profile.isComplete()).isFalse();
        assertThat(profile.isRequired()).isFalse();
    }

    @Test
    void legacyExemptSurvivesVoluntaryCompletion() {
        // 파생값이 아니라 마이그레이션이 찍은 사실이므로 값이 뒤집히면 안 된다.
        OnboardingProfile profile = OnboardingProfile.empty()
                .asLegacyExempt()
                .withNickname(new Nickname("고레"))
                .withVerifiedPhone(new PhoneNumber("01012345678"), NOW)
                .withInterestTags(Set.of("technic"))
                .withConsents(NOW, null);

        assertThat(profile.legacyExempt()).isTrue();
        assertThat(profile.isComplete()).isTrue();
    }

    @Test
    void interestTagSelectionIsBoundedToOneThroughFive() {
        Set<String> sixKeys = InterestTagCatalog.tags().subList(0, 6).stream()
                .map(InterestTag::key)
                .collect(Collectors.toSet());

        assertThatThrownBy(() -> InterestTagCatalog.validateSelection(Set.of()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> InterestTagCatalog.validateSelection(sixKeys))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("1~5개");
    }

    @Test
    void interestTagOutsideCatalogIsRejected() {
        assertThatThrownBy(() -> InterestTagCatalog.validateSelection(Set.of("직접입력한테마")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void interestTagLabelIsNotAcceptedAsAKey() {
        // 저장되는 값은 key다. label을 그대로 돌려보내면 문구를 고치는 순간 저장된 선택이 깨진다.
        assertThatThrownBy(() -> InterestTagCatalog.validateSelection(Set.of("테크닉")))
                .isInstanceOf(BadRequestException.class);
        assertThat(InterestTagCatalog.validateSelection(Set.of("technic"))).containsExactly("technic");
    }

    @Test
    void everyCatalogTagHasAStableKeyAndLabel() {
        assertThat(InterestTagCatalog.tags()).hasSizeBetween(10, 15).allSatisfy(tag -> {
            assertThat(tag.key()).matches("[a-z][a-z-]*");
            assertThat(tag.label()).isNotBlank();
        });
        assertThat(InterestTagCatalog.tags()).extracting(InterestTag::key).doesNotHaveDuplicates();
    }

    private static OnboardingProfile complete() {
        return new OnboardingProfile(
                new Nickname("고레"), new PhoneNumber("01012345678"), NOW, Set.of("technic"), NOW, null, false);
    }
}
