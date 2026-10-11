package com.gole.api.listing.adapter.out.account;

import com.gole.api.account.application.port.in.VerifySellerIdentityUseCase;
import com.gole.api.listing.application.port.out.ListingSellerVerificationPort;
import org.springframework.stereotype.Component;

/** 계정 컨텍스트 통합 어댑터. 판매자 신원확인 판정을 account 의 인바운드 포트에 맡긴다(주문·제안·채팅과 같은 판정). */
@Component
public class ListingSellerVerificationAdapter implements ListingSellerVerificationPort {

    private final VerifySellerIdentityUseCase sellerIdentityVerification;

    public ListingSellerVerificationAdapter(VerifySellerIdentityUseCase sellerIdentityVerification) {
        this.sellerIdentityVerification = sellerIdentityVerification;
    }

    @Override
    public void requireVerifiedSeller(String sellerId) {
        sellerIdentityVerification.requireVerifiedSeller(sellerId);
    }
}
