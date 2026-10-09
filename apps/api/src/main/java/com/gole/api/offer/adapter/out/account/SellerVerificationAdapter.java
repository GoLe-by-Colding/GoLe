package com.gole.api.offer.adapter.out.account;

import com.gole.api.account.application.service.SellerIdentityVerificationService;
import com.gole.api.offer.application.port.out.SellerVerificationPort;
import org.springframework.stereotype.Component;

/**
 * 계정 컨텍스트 통합 어댑터. 판매자 신원확인 판정을 주문·채팅방 생성과 같은 서비스에 맡긴다.
 *
 * <p>계정 컨텍스트에는 이 판정의 인바운드 포트가 따로 없고, 주문·채팅 웹 어댑터도 같은 서비스를 직접
 * 쓴다. 판정 규칙이 갈라지지 않게 여기서도 그 서비스 하나를 쓴다.
 */
@Component
public class SellerVerificationAdapter implements SellerVerificationPort {

    private final SellerIdentityVerificationService sellerIdentityVerification;

    public SellerVerificationAdapter(SellerIdentityVerificationService sellerIdentityVerification) {
        this.sellerIdentityVerification = sellerIdentityVerification;
    }

    @Override
    public void requireVerifiedSeller(String sellerId) {
        sellerIdentityVerification.requireVerifiedSeller(sellerId);
    }
}
