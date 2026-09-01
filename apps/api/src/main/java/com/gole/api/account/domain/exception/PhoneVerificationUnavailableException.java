package com.gole.api.account.domain.exception;

import com.gole.api.common.exception.DomainException;

/**
 * 전화번호 인증 발송 경로가 아직 준비되지 않았을 때. (onboarding D3)
 *
 * <p>카카오 알림톡 템플릿 승인은 코드 밖 운영 과제라 설정이 비어 있는 기간이 실제로 존재한다.
 * 그 상태에서 조용히 성공을 돌려주면 사용자는 오지 않는 코드를 기다리게 된다 — 명시적으로
 * 실패시켜 운영이 알아차리게 한다.
 */
public class PhoneVerificationUnavailableException extends DomainException {

    public PhoneVerificationUnavailableException() {
        super("PHONE_VERIFICATION_UNAVAILABLE", "전화번호 인증을 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요");
    }
}
