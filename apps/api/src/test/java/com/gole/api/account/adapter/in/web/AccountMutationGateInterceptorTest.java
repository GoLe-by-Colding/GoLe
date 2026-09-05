package com.gole.api.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.concurrency.AccountMutationGate;
import com.gole.api.account.application.concurrency.AccountMutationGate.Lease;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.UnauthorizedException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AccountMutationGateInterceptorTest {

    private final AccountMutationGate gate = mock(AccountMutationGate.class);
    private final Lease lease = mock(Lease.class);
    private final GetCurrentSessionUseCase sessions = mock(GetCurrentSessionUseCase.class);
    private final AccountMutationGateInterceptor interceptor =
            new AccountMutationGateInterceptor(gate, sessions, new SessionCookie("false"));

    @ParameterizedTest
    @CsvSource({
        "PUT, /api/v1/accounts/me/onboarding/nickname",
        "POST, /api/v1/accounts/me/onboarding/consent",
        "POST, /api/v1/accounts/me/third-party-provision-consents",
        "PUT, /api/v1/accounts/password",
        "POST, /api/v1/accounts/sessions/refresh",
        "DELETE, /api/v1/accounts/sessions",
        "PATCH, /api/v2/community/posts/post-1"
    })
    void validSessionMutationIsRevalidatedInsideSharedLease(String method, String path) {
        MockHttpServletRequest request = authenticated(method, path);
        CurrentSession session = session("account-1");
        when(sessions.resolve("session-token")).thenReturn(Optional.of(session));
        when(gate.acquireShared("account-1")).thenReturn(lease);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();

        verify(sessions, times(2)).resolve("session-token");
        verify(gate).acquireShared("account-1");
        assertThat(request.getAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID)).isEqualTo("account-1");
    }

    @Test
    void missingSessionOnPublicMutationDoesNotCreateGateEntry() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/accounts/password-reset");
        when(sessions.resolve("")).thenReturn(Optional.empty());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();

        verify(gate, never()).acquireShared(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void deletionRequestUsesControllerOwnedExclusiveLease() {
        MockHttpServletRequest request = authenticated("POST", "/api/v1/accounts/me/deletion-requests");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();

        verify(sessions, never()).resolve(org.mockito.ArgumentMatchers.anyString());
        verify(gate, never()).acquireShared(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void safeReadDoesNotAcquireLease() {
        MockHttpServletRequest request = authenticated("GET", "/api/v1/accounts/me");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();

        verify(sessions, never()).resolve(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void suspensionBetweenInitialResolutionAndLeaseRejectsAndReleases() {
        MockHttpServletRequest request = authenticated("POST", "/api/v1/listings");
        when(sessions.resolve("session-token")).thenReturn(Optional.of(session("account-1")), Optional.empty());
        when(gate.acquireShared("account-1")).thenReturn(lease);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_SESSION");

        verify(lease).close();
        assertThat(request.getAttribute(AccountMutationGateInterceptor.ATTR_LEASE))
                .isNull();
    }

    @Test
    void changedSessionPrincipalIsRejectedAndReleases() {
        MockHttpServletRequest request = authenticated("POST", "/api/v1/listings");
        when(sessions.resolve("session-token"))
                .thenReturn(Optional.of(session("account-1")), Optional.of(session("account-2")));
        when(gate.acquireShared("account-1")).thenReturn(lease);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class);

        verify(lease).close();
    }

    @Test
    void completionReleasesLeaseEvenAfterHandlerException() {
        MockHttpServletRequest request = authenticated("POST", "/api/v1/listings");
        when(sessions.resolve("session-token")).thenReturn(Optional.of(session("account-1")));
        when(gate.acquireShared("account-1")).thenReturn(lease);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Object handler = new Object();
        interceptor.preHandle(request, response, handler);

        interceptor.afterCompletion(request, response, handler, new IllegalStateException("handler failed"));
        interceptor.afterCompletion(request, response, handler, null);

        verify(lease).close();
        assertThat(request.getAttribute(AccountMutationGateInterceptor.ATTR_LEASE))
                .isNull();
    }

    @Test
    void asyncRedispatchKeepsSingleLeaseUntilFinalCompletion() throws Exception {
        MockHttpServletRequest request = authenticated("POST", "/api/v1/listings");
        when(sessions.resolve("session-token")).thenReturn(Optional.of(session("account-1")));
        when(gate.acquireShared("account-1")).thenReturn(lease);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Object handler = new Object();

        interceptor.preHandle(request, response, handler);
        interceptor.afterConcurrentHandlingStarted(request, response, handler);
        interceptor.preHandle(request, response, handler);

        verify(sessions, times(2)).resolve("session-token");
        verify(gate).acquireShared("account-1");
        verify(lease, never()).close();

        interceptor.afterCompletion(request, response, handler, null);
        verify(lease).close();
    }

    private static MockHttpServletRequest authenticated(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer session-token");
        return request;
    }

    private static CurrentSession session(String accountId) {
        return new CurrentSession(accountId, accountId + "@gole.test", Role.USER);
    }
}
