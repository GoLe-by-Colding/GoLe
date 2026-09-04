package com.gole.api.account.domain.model;

/**
 * 신규 계정 생성 전에 사용자가 직접 확인한 정책 정보.
 *
 * <p>클라이언트가 보낸 버전과 현재 서버 버전이 일치하는지는 애플리케이션 서비스에서 다시
 * 검증한다. {@code privacyAcknowledged}는 서비스 제공에 필요한 처리방침을 읽었다는 확인이며,
 * 선택 동의를 하나의 필수 동의로 뭉치지 않는다.
 */
public record SignupPolicyAcceptance(
        String termsVersion,
        String privacyVersion,
        boolean termsAccepted,
        boolean privacyAcknowledged,
        boolean minimumAgeConfirmed,
        String thirdPartyProvisionVersion,
        boolean thirdPartyProvisionAccepted) {

    /** 기존 내부 호출과 직렬화 자료가 선택 동의를 강제로 참으로 만들지 않게 하는 호환 생성자. */
    public SignupPolicyAcceptance(
            String termsVersion,
            String privacyVersion,
            boolean termsAccepted,
            boolean privacyAcknowledged,
            boolean minimumAgeConfirmed) {
        this(termsVersion, privacyVersion, termsAccepted, privacyAcknowledged, minimumAgeConfirmed, null, false);
    }
}
