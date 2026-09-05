package com.gole.api.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.adapter.in.web.SocialAuthController.AuthorizeUrlRequest;
import com.gole.api.account.adapter.in.web.SocialAuthController.CallbackRequest;
import com.gole.api.account.application.port.in.PublicAuthRequestLimitUseCase;
import com.gole.api.account.application.port.in.SocialLoginUseCase;
import com.gole.api.account.application.port.in.SocialLoginUseCase.AuthorizeUrlResult;
import com.gole.api.account.application.port.in.SocialLoginUseCase.SocialLoginResult;
import com.gole.api.account.domain.model.AuthProvider;
import com.gole.api.account.domain.model.Role;
import com.gole.api.account.domain.model.SignupPolicyAcceptance;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.web.ClientAddressResolver;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SocialAuthControllerTest {

    private SocialLoginUseCase socialLogin;
    private PublicAuthRequestLimitUseCase publicRequestLimit;
    private SocialAuthController controller;

    @BeforeEach
    void setUp() {
        socialLogin = mock(SocialLoginUseCase.class);
        publicRequestLimit = mock(PublicAuthRequestLimitUseCase.class);
        controller = new SocialAuthController(
                socialLogin,
                new SessionCookie("false"),
                new OAuthTransactionCookie("false"),
                publicRequestLimit,
                new ClientAddressResolver());
    }

    @Test
    void authorizeUrlBindsReturnedStateToHttpOnlyBrowserCookie() {
        when(socialLogin.authorizeUrl(any(), any(), nullable(SignupPolicyAcceptance.class), nullable(String.class)))
                .thenReturn(new AuthorizeUrlResult("https://accounts.example/authorize", "state-1"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.authorizeUrl(
                "google",
                new AuthorizeUrlRequest(
                        "https://gole.co.kr/auth/callback/google", null, null, null, null, null, null, null, null),
                new MockHttpServletRequest(),
                response);

        assertThat(result.url()).isEqualTo("https://accounts.example/authorize");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("gole_oauth_transaction=state-1", "HttpOnly", "SameSite=Lax");
        verify(publicRequestLimit).acquireOAuthAuthorization("127.0.0.1");
    }

    @Test
    void authorizeUrlPassesOptionalThirdPartyChoiceIntoServerBoundStateContext() {
        when(socialLogin.authorizeUrl(any(), any(), any(SignupPolicyAcceptance.class), any()))
                .thenReturn(new AuthorizeUrlResult("https://accounts.example/authorize", "state-1"));
        ArgumentCaptor<SignupPolicyAcceptance> acceptance = ArgumentCaptor.forClass(SignupPolicyAcceptance.class);

        controller.authorizeUrl(
                "google",
                new AuthorizeUrlRequest(
                        "https://gole.co.kr/auth/callback/google",
                        "2026-09-04",
                        "2026-09-05",
                        true,
                        true,
                        true,
                        "2026-09-04",
                        true,
                        "/chat"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse());

        verify(socialLogin)
                .authorizeUrl(
                        eq(AuthProvider.GOOGLE),
                        eq("https://gole.co.kr/auth/callback/google"),
                        acceptance.capture(),
                        eq("/chat"));
        assertThat(acceptance.getValue().thirdPartyProvisionVersion()).isEqualTo("2026-09-04");
        assertThat(acceptance.getValue().thirdPartyProvisionAccepted()).isTrue();
    }

    @Test
    void callbackRejectsStateFromAnotherBrowserBeforeCodeExchange() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(OAuthTransactionCookie.NAME, "victim-state"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.callback(
                        "google",
                        new CallbackRequest(
                                "provider-code", "https://gole.co.kr/auth/callback/google", "attacker-state"),
                        request,
                        response))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", "OAUTH_STATE_INVALID");

        verify(socialLogin, never()).login(any());
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("gole_oauth_transaction=", "Max-Age=0");
    }

    @Test
    void callbackConsumesBrowserBindingAndIssuesSessionCookie() {
        when(socialLogin.login(any()))
                .thenReturn(new SocialLoginResult("account-1", "session-1", Role.USER, false, false, "/prices"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(OAuthTransactionCookie.NAME, "state-1"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.callback(
                "google",
                new CallbackRequest("provider-code", "https://gole.co.kr/auth/callback/google", "state-1"),
                request,
                response);

        assertThat(result.accountId()).isEqualTo("account-1");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(value -> assertThat(value).contains("gole_session=session-1"))
                .anySatisfy(value -> assertThat(value).contains("gole_oauth_transaction=", "Max-Age=0"));
        verify(socialLogin).login(any());
    }
}
