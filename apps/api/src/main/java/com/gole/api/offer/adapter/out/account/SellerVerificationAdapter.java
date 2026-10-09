package com.gole.api.offer.adapter.out.account;

import com.gole.api.account.application.port.in.VerifySellerIdentityUseCase;
import com.gole.api.offer.application.port.out.SellerVerificationPort;
import org.springframework.stereotype.Component;

/**
 * 계정 컨텍스트 통합 어댑터. 판매자 신원확인 판정을 account 의 인바운드 포트에 맡긴다.
 *
 * <p>주문·채팅방 생성·매물 문의도 같은 포트({@code VerifySellerIdentityUseCase})를 써서 판정 규칙이 갈라지지 않는다.
 */
@Component
public class SellerVerificationAdapter implements SellerVerificationPort {

    private final VerifySellerIdentityUseCase sellerIdentityVerification;

    public SellerVerificationAdapter(VerifySellerIdentityUseCase sellerIdentityVerification) {
        this.sellerIdentityVerification = sellerIdentityVerification;
    }

    @Override
    public void requireVerifiedSeller(String sellerId) {
        sellerIdentityVerification.requireVerifiedSeller(sellerId);
    }
}
