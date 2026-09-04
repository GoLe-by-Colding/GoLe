package com.gole.api.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OAuthTransactionCookieTest {

    @Test
    void issueBindsStateToSecureHttpOnlyShortLivedCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OAuthTransactionCookie("true").issue(new MockHttpServletRequest(), response, "state-1");

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains(
                        "gole_oauth_transaction=state-1",
                        "Path=/api/v1/auth/oauth",
                        "Max-Age=600",
                        "Secure",
                        "HttpOnly",
                        "SameSite=Lax");
    }

    @Test
    void matchesOnlyTheStateIssuedToThisBrowser() {
        OAuthTransactionCookie transactions = new OAuthTransactionCookie("false");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(OAuthTransactionCookie.NAME, "state-1"));

        assertThat(transactions.matches(request, "state-1")).isTrue();
        assertThat(transactions.matches(request, "attacker-state")).isFalse();
        assertThat(transactions.matches(new MockHttpServletRequest(), "state-1"))
                .isFalse();
    }

    @Test
    void clearExpiresOnlyTheOAuthTransactionPath() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OAuthTransactionCookie("true").clear(new MockHttpServletRequest(), response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("gole_oauth_transaction=", "Path=/api/v1/auth/oauth", "Max-Age=0", "Secure", "HttpOnly");
    }
}
