package com.gole.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 개인 판매자 신원확인 운영 준비 상태.
 *
 * <p>전화번호를 인증한 계정이 있더라도, 운영자가 판매자 신원확인 절차 전체를 준비했다고 배포 설정에서 명시하지 않으면 신규 매물 등록은 열리지 않는다. 기본값을 닫아 두어
 * 누락된 환경변수가 판매 기능 개방으로 해석되지 않게 한다.
 */
@ConfigurationProperties(prefix = "gole.seller-identity")
public class SellerIdentityVerificationProperties {

    private boolean verificationReady = false;

    public boolean verificationReady() {
        return verificationReady;
    }

    public void setVerificationReady(boolean verificationReady) {
        this.verificationReady = verificationReady;
    }
}
