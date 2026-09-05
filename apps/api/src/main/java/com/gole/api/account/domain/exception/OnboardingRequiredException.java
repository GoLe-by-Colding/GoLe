package com.gole.api.account.domain.exception;

import com.gole.api.common.exception.ForbiddenException;

/**
 * 온보딩 미완료 계정이 거래성 액션을 시도했을 때. (onboarding D5, R9)
 *
 * <p>{@link ForbiddenException}을 상속하므로 {@code GlobalExceptionHandler}가 이미 403으로
 * 매핑한다 — 별도 핸들러를 더하지 않는다. 프론트는 이 {@code code}를 보고 온보딩으로 유도한다.
 */
public class OnboardingRequiredException extends ForbiddenException {

    public OnboardingRequiredException() {
        super("ONBOARDING_REQUIRED", "프로필 설정을 완료해야 이용할 수 있습니다");
    }
}
