package com.gole.api.account.application.port.in;

/** 가입 화면이 서버의 현재 정책 버전과 최소 연령을 읽는 공개 포트. */
public interface GetCurrentSignupPolicyUseCase {

    CurrentSignupPolicy currentSignupPolicy();

    record CurrentSignupPolicy(
            String termsVersion, String privacyVersion, String thirdPartyProvisionVersion, int minimumAge) {}
}
