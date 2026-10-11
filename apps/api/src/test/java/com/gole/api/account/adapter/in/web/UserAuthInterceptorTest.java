package com.gole.api.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.UnauthorizedException;
import com.gole.api.common.web.auth.SessionCookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class UserAuthInterceptorTest {

    private final GetCurrentSessionUseCase sessions = mock(GetCurrentSessionUseCase.class);
    private final UserAuthInterceptor interceptor = new UserAuthInterceptor(sessions, new SessionCookie("false"));

    @Test
    void publicGetDoesNotRequireSession() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();
    }

    @Test
    void publicGetAttachesViewerWhenValidSessionIsPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/community/posts");
        request.addHeader("Authorization", "Bearer session-1");
        when(sessions.resolve("session-1"))
                .thenReturn(Optional.of(new CurrentSession("account-1", "member@gole.test", Role.USER)));

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();
        assertThat(request.getAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID)).isEqualTo("account-1");
    }

    @Test
    void publicGetIgnoresExpiredSessionInsteadOfBlockingPage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/community/posts");
        request.addHeader("Authorization", "Bearer expired");
        when(sessions.resolve("expired")).thenReturn(Optional.empty());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();
        assertThat(request.getAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID)).isNull();
    }

    @Test
    void publicFeeConfigDoesNotRequireSession() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/config/fees");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();
        verify(sessions, never()).resolve(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void writeRequiresValidSessionAndSetsActor() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/listings");
        request.addHeader("Authorization", "Bearer session-1");
        when(sessions.resolve("session-1"))
                .thenReturn(Optional.of(new CurrentSession("account-1", "member@gole.test", Role.USER)));

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();
        assertThat(request.getAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID)).isEqualTo("account-1");
    }

    @Test
    void missingSessionRejectsWrite() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/listings");
        when(sessions.resolve("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void orderReadRequiresSession() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders/order-1");
        when(sessions.resolve("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void followingCommunityFeedRequiresSession() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/community/feed/following");
        when(sessions.resolve("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/offers", "/api/v1/bids/mine", "/api/v1/part-requests/mine"})
    void sessionScopedCommerceReadsRequireSession(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        when(sessions.resolve("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/bids/book/75192", "/api/v1/part-requests", "/api/v1/part-requests/request-1"})
    void publicCommerceReadsDoNotRequireSession(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();
    }
}
