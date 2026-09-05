package com.gole.api.account.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.gole.api.account.adapter.in.web.AccountMutationGateInterceptor;
import com.gole.api.account.adapter.in.web.OnboardingGuardInterceptor;
import com.gole.api.account.adapter.in.web.SellerIdentityGuardInterceptor;
import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

class UserWebConfigTest {

    @Test
    void sessionGuard_matchesV2CommunityPatchWithoutChangingV1WebhookExceptions() {
        InspectableInterceptorRegistry registry = new InspectableInterceptorRegistry();
        new UserWebConfig(
                        mock(UserAuthInterceptor.class),
                        mock(OnboardingGuardInterceptor.class),
                        mock(AccountMutationGateInterceptor.class),
                        mock(SellerIdentityGuardInterceptor.class))
                .addInterceptors(registry);
        MappedInterceptor mutationGate =
                (MappedInterceptor) registry.registered().get(0);
        MappedInterceptor guard = (MappedInterceptor) registry.registered().get(1);
        MappedInterceptor sellerIdentityGuard =
                (MappedInterceptor) registry.registered().get(3);

        assertThat(mutationGate.matches(request("PUT", "/api/v1/accounts/me/onboarding/nickname")))
                .isTrue();
        assertThat(mutationGate.matches(request("POST", "/api/v1/accounts/me/third-party-provision-consents")))
                .isTrue();
        assertThat(mutationGate.matches(request("PUT", "/api/v1/accounts/password")))
                .isTrue();
        assertThat(mutationGate.matches(request("POST", "/api/v1/accounts/sessions/refresh")))
                .isTrue();
        assertThat(mutationGate.matches(request("DELETE", "/api/v1/accounts/sessions")))
                .isTrue();
        assertThat(mutationGate.matches(request("POST", "/api/admin/account-deletions/request-1/complete")))
                .isTrue();

        assertThat(guard.matches(request("PATCH", "/api/v2/community/posts/post-1")))
                .isTrue();
        assertThat(guard.matches(request("PUT", "/api/v1/community/posts/post-1")))
                .isTrue();
        assertThat(guard.matches(request("POST", "/api/v1/payments/portone/webhook")))
                .isFalse();
        assertThat(guard.matches(request("POST", "/api/v1/auth/login"))).isFalse();
        assertThat(guard.matches(request("GET", "/api/v1/policies/current"))).isFalse();
        assertThat(sellerIdentityGuard.matches(request("POST", "/api/v1/listings")))
                .isTrue();
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        ServletRequestPathUtils.parseAndCache(request);
        return request;
    }

    private static final class InspectableInterceptorRegistry extends InterceptorRegistry {

        List<Object> registered() {
            return super.getInterceptors();
        }
    }
}
