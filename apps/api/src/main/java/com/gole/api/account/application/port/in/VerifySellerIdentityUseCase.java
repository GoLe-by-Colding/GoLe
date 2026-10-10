package com.gole.api.account.application.port.in;

/**
 * Inbound port: 판매자 신원확인 판정. 신규 거래를 여는 다른 컨텍스트(order·offer·chat·listing)가 이것만 본다.
 *
 * <p>운영 준비 래치와 계정의 실제 전화 인증 상태를 함께 본다. legacy 온보딩 면제나 관리자 런치 체크는 이 판정을 우회하지 못한다.
 */
public interface VerifySellerIdentityUseCase {

    /** 판매자 신원확인을 받을 운영 준비가 끝났는가(배포 래치). */
    boolean isRuntimeReady();

    /**
     * 래치가 열리고 대상 판매자 계정에 인증된 전화번호가 실제로 저장된 경우만 통과한다.
     *
     * @throws com.gole.api.common.exception.ServiceUnavailableException 래치가 닫혀 있을 때
     * @throws com.gole.api.account.domain.exception.SellerIdentityVerificationRequiredException 인증이 없을 때
     */
    void requireVerifiedSeller(String sellerAccountId);
}
