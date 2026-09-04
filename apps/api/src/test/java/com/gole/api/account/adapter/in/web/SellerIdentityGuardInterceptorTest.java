package com.gole.api.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gole.api.account.application.service.SellerIdentityVerificationService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class SellerIdentityGuardInterceptorTest {

    private final SellerIdentityVerificationService verification = mock(SellerIdentityVerificationService.class);
    private final SellerIdentityGuardInterceptor interceptor = new SellerIdentityGuardInterceptor(verification);

    @Test
    void guardedActionChecksAuthenticatedAccountOnServer() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/listings");
        request.setAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID, "seller-1");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler("guarded")))
                .isTrue();

        verify(verification).requireVerifiedSeller("seller-1");
    }

    @Test
    void browsingAndCorsPreflightRemainOpen() {
        MockHttpServletRequest browse = new MockHttpServletRequest("GET", "/api/v1/listings");
        MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/api/v1/listings");

        assertThat(interceptor.preHandle(browse, new MockHttpServletResponse(), handler("browse")))
                .isTrue();
        assertThat(interceptor.preHandle(preflight, new MockHttpServletResponse(), handler("guarded")))
                .isTrue();
        verify(verification, never()).requireVerifiedSeller(anyString());
    }

    private static HandlerMethod handler(String methodName) {
        try {
            Method method = SampleController.class.getMethod(methodName);
            return new HandlerMethod(new SampleController(), method);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static class SampleController {

        @RequiresVerifiedSellerIdentity
        public void guarded() {}

        public void browse() {}
    }
}
