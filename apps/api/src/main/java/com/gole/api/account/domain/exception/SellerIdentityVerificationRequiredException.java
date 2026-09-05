package com.gole.api.account.domain.exception;

import com.gole.api.common.exception.ForbiddenException;

/** 인증된 전화번호가 없는 계정이 개인 판매자로 신규 매물을 등록하려 할 때. */
public class SellerIdentityVerificationRequiredException extends ForbiddenException {

    public SellerIdentityVerificationRequiredException() {
        super("SELLER_IDENTITY_VERIFICATION_REQUIRED", "판매자 신원확인이 완료되지 않아 신규 거래 연결을 시작할 수 없습니다");
    }
}
