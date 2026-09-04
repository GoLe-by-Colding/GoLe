package com.gole.api.account.adapter.in.web;

import com.gole.api.account.application.service.SellerIdentityVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 신규 판매 액션의 서버 신원확인 가드.
 *
 * <p>관리자 런치 체크와 별개인 배포 설정과 계정의 실제 전화번호 인증 시각을 모두 확인한다. 따라서 화면을 우회하거나 관리자 준비 체크만 누르는 것으로 매물을 등록할 수
 * 없다.
 */
@Component
public class SellerIdentityGuardInterceptor implements HandlerInterceptor {

    private final SellerIdentityVerificationService sellerIdentityVerification;

    public SellerIdentityGuardInterceptor(SellerIdentityVerificationService sellerIdentityVerification) {
        this.sellerIdentityVerification = sellerIdentityVerification;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod method)
                || !method.hasMethodAnnotation(RequiresVerifiedSellerIdentity.class)) {
            return true;
        }
        Object accountId = request.getAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID);
        String id = accountId instanceof String value ? value : null;
        sellerIdentityVerification.requireVerifiedSeller(id);
        return true;
    }
}
