package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.GetOnboardingStatusUseCase;
import com.gole.api.account.domain.exception.SellerIdentityVerificationRequiredException;
import com.gole.api.common.config.SellerIdentityVerificationProperties;
import com.gole.api.common.exception.ServiceUnavailableException;
import org.springframework.stereotype.Service;

/** 판매자 신원확인 배포 래치와 계정의 실제 인증 상태를 한 곳에서 판정한다. */
@Service
public class SellerIdentityVerificationService {

    private final GetOnboardingStatusUseCase onboardingStatus;
    private final SellerIdentityVerificationProperties properties;

    public SellerIdentityVerificationService(
            GetOnboardingStatusUseCase onboardingStatus, SellerIdentityVerificationProperties properties) {
        this.onboardingStatus = onboardingStatus;
        this.properties = properties;
    }

    public boolean isRuntimeReady() {
        return properties.verificationReady();
    }

    /**
     * 운영 준비 래치가 열리고, 대상 판매자 계정에 인증된 전화번호가 실제로 저장된 경우만 통과한다. legacy 온보딩 면제나 관리자 런치 체크는 이 판정을 우회하지 못한다.
     */
    public void requireVerifiedSeller(String sellerAccountId) {
        if (!isRuntimeReady()) {
            throw new ServiceUnavailableException(
                    "SELLER_IDENTITY_VERIFICATION_UNAVAILABLE", "판매자 신원확인 준비 중으로 신규 거래 연결을 잠시 받지 않습니다");
        }
        if (sellerAccountId == null
                || sellerAccountId.isBlank()
                || !onboardingStatus.status(sellerAccountId).phoneCompleted()) {
            throw new SellerIdentityVerificationRequiredException();
        }
    }
}
