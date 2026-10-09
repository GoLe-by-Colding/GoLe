package com.gole.api.chat.adapter.out.account;

import com.gole.api.account.application.port.in.VerifySellerIdentityUseCase;
import com.gole.api.chat.application.port.out.ChatSellerVerificationPort;
import org.springframework.stereotype.Component;

/** 계정 컨텍스트 통합 어댑터. 판매자 신원확인을 account 의 인바운드 포트에 맡긴다(주문·제안·매물 문의와 같은 판정). */
@Component
public class AccountChatSellerVerificationAdapter implements ChatSellerVerificationPort {

    private final VerifySellerIdentityUseCase sellerIdentityVerification;

    public AccountChatSellerVerificationAdapter(VerifySellerIdentityUseCase sellerIdentityVerification) {
        this.sellerIdentityVerification = sellerIdentityVerification;
    }

    @Override
    public void requireVerifiedSeller(String sellerId) {
        sellerIdentityVerification.requireVerifiedSeller(sellerId);
    }
}
