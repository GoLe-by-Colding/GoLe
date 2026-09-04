package com.gole.api.account.adapter.in.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** OAuth state를 로그인 시작 브라우저의 HttpOnly 쿠키에 결박해 login CSRF를 차단한다. */
@Component
public class OAuthTransactionCookie {

    static final String NAME = "gole_oauth_transaction";
    private static final String PATH = "/api/v1/auth/oauth";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final String secureMode;

    public OAuthTransactionCookie(@Value("${gole.session.cookie-secure:auto}") String secureMode) {
        this.secureMode = secureMode;
    }

    public void issue(HttpServletRequest request, HttpServletResponse response, String state) {
        response.addHeader(
                HttpHeaders.SET_COOKIE, cookie(state, TTL, isSecure(request)).toString());
    }

    public boolean matches(HttpServletRequest request, String returnedState) {
        if (returnedState == null || returnedState.isBlank()) {
            return false;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())) {
                return MessageDigest.isEqual(
                        cookie.getValue().getBytes(StandardCharsets.UTF_8),
                        returnedState.getBytes(StandardCharsets.UTF_8));
            }
        }
        return false;
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie("", Duration.ZERO, isSecure(request)).toString());
    }

    private static ResponseCookie cookie(String value, Duration maxAge, boolean secure) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }

    private boolean isSecure(HttpServletRequest request) {
        if ("true".equalsIgnoreCase(secureMode)) {
            return true;
        }
        if ("false".equalsIgnoreCase(secureMode)) {
            return false;
        }
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }
}
