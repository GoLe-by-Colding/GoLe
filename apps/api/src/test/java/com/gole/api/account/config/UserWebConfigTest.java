package com.gole.api.account.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
        new UserWebConfig(mock(UserAuthInterceptor.class)).addInterceptors(registry);
        MappedInterceptor guard = (MappedInterceptor) registry.registered().getFirst();

        assertThat(guard.matches(request("PATCH", "/api/v2/community/posts/post-1")))
                .isTrue();
        assertThat(guard.matches(request("PUT", "/api/v1/community/posts/post-1")))
                .isTrue();
        assertThat(guard.matches(request("POST", "/api/v1/payments/portone/webhook")))
                .isFalse();
        assertThat(guard.matches(request("POST", "/api/v1/auth/login"))).isFalse();
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
