package com.gole.api.account.application.port.in;

import java.util.List;

/**
 * Inbound port: 온보딩 진행 상태 조회. (onboarding R2)
 *
 * <p>재개용이자 게이팅 판정의 유일한 창구다. 다른 컨텍스트(listing/order/chat)의 가드는
 * 저장소나 Document가 아니라 이 포트만 호출한다 — "다른 컨텍스트의 인바운드 포트에만 의존".
 */
public interface GetOnboardingStatusUseCase {

    OnboardingStatus status(String accountId);

    /**
     * @param maskedPhoneNumber 인증된 번호의 마스킹 표기. 미인증이면 null.
     * @param phoneVerificationRequired 현재 배포에서 전화 인증을 완료 조건으로 요구하는가.
     * @param required 아직 온보딩을 요구해야 하는가(파생값, D1). legacyExempt면 항상 false.
     */
    record OnboardingStatus(
            String accountId,
            boolean nicknameCompleted,
            String nickname,
            boolean phoneVerificationRequired,
            boolean phoneCompleted,
            String maskedPhoneNumber,
            boolean interestTagsCompleted,
            List<String> interestTags,
            boolean privacyConsented,
            boolean marketingConsented,
            boolean required,
            boolean legacyExempt) {}
}
