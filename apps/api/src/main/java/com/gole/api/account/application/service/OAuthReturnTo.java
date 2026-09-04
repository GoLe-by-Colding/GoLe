package com.gole.api.account.application.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/** OAuth state에 결박할 동일 오리진 상대 복귀 경로 검증기. */
final class OAuthReturnTo {

    private static final int MAX_LENGTH = 512;
    private static final List<String> DENIED_PREFIXES = List.of("/login", "/signup", "/verify", "/auth", "/onboarding");

    private OAuthReturnTo() {}

    static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.length() > MAX_LENGTH || !value.startsWith("/") || hasUnsafeCharacter(value)) {
            return null;
        }

        try {
            URI parsed = new URI(value);
            if (parsed.isAbsolute() || parsed.getRawAuthority() != null) {
                return null;
            }

            String decodedPath = parsed.getPath();
            if (decodedPath == null
                    || !decodedPath.startsWith("/")
                    || decodedPath.startsWith("//")
                    || decodedPath.contains("\\")
                    || hasDotSegment(decodedPath)
                    || hasUnsafeCharacter(decodedPath)
                    || hasUnsafeCharacter(parsed.getQuery())
                    || hasUnsafeCharacter(parsed.getFragment())) {
                return null;
            }

            URI normalized = parsed.normalize();
            String path = normalized.getPath();
            if (isDenied(path)) {
                return null;
            }
            return normalized.toString();
        } catch (URISyntaxException invalid) {
            return null;
        }
    }

    private static boolean isDenied(String path) {
        return DENIED_PREFIXES.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private static boolean hasDotSegment(String path) {
        for (String segment : path.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnsafeCharacter(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character) || character <= 0x1f || character == 0x7f) {
                return true;
            }
        }
        return false;
    }
}
