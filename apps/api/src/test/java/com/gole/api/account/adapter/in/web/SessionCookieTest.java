package com.gole.api.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionCookieTest {

    @Test
    void issuesHttpOnlySameSiteSecureCookieInProduction() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest();

        new SessionCookie("true").issue(request, response, "secret-token");

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains(
                        "gole_session=secret-token", "Path=/", "Max-Age=604800", "Secure", "HttpOnly", "SameSite=Lax");
    }

    @Test
    void bearerWinsAndCookieIsFallback() {
        SessionCookie sessions = new SessionCookie("false");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SessionCookie.NAME, "cookie-token"));

        assertThat(sessions.resolve(request)).isEqualTo("cookie-token");
        assertThat(sessions.usesBearer(request)).isFalse();

        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer header-token");
        assertThat(sessions.resolve(request)).isEqualTo("header-token");
        assertThat(sessions.usesBearer(request)).isTrue();
    }

    @Test
    void autoModeMarksCookieSecureBehindHttpsProxy() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");

        new SessionCookie("auto").issue(request, response, "secret-token");

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Secure", "HttpOnly");
    }

    @Test
    void autoModeKeepsLocalHttpCookieCredentialedButNotSecure() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest();

        new SessionCookie("auto").issue(request, response, "secret-token");

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("gole_session=secret-token", "Path=/", "HttpOnly", "SameSite=Lax")
                .doesNotContain("Secure", "Domain=");
    }

    @Test
    void clearExpiresCookieImmediately() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest();

        new SessionCookie("true").clear(request, response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("gole_session=", "Max-Age=0");
    }

    @Test
    void refreshCookieUsesRemainingAbsoluteLifetime() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest();

        new SessionCookie("false", java.time.Duration.ofDays(7))
                .issue(request, response, "rotated-token", java.time.Duration.ofHours(6));

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("gole_session=rotated-token", "Max-Age=21600")
                .doesNotContain("Secure");
    }
}
