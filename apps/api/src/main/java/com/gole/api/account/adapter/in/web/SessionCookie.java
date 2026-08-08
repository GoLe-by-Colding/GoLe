package com.gole.api.account.adapter.in.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** 웹 세션용 HttpOnly 쿠키 발급·해석. Bearer 헤더는 API 클라이언트 호환을 위해 별도로 유지한다. */
@Component
public class SessionCookie {

    public static final String NAME = "gole_session";
    private static final Duration TTL = Duration.ofDays(7);
    private final String secureMode;

    public SessionCookie(@Value("${gole.session.cookie-secure:auto}") String secureMode) {
        this.secureMode = secureMode;
    }

    public void issue(HttpServletRequest request, HttpServletResponse response, String token) {
        response.addHeader(
                HttpHeaders.SET_COOKIE, cookie(token, TTL, isSecure(request)).toString());
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie("", Duration.ZERO, isSecure(request)).toString());
    }

    public String resolve(HttpServletRequest request) {
        String bearer = bearer(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (!bearer.isBlank()) {
            return bearer;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return "";
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return "";
    }

    private ResponseCookie cookie(String value, Duration maxAge, boolean secure) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
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

    private static String bearer(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim()
                : "";
    }
}
