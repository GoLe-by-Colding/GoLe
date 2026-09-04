package com.gole.api.media.domain.model;

import java.util.Optional;
import java.util.regex.Pattern;

/** 신뢰 가능한 사용자 객체 키와 공개 경로 변환의 단일 경계. */
public final class MediaKey {

    public static final String PUBLIC_PATH_PREFIX = "/api/v1/media/";

    private static final Pattern USER_KEY = Pattern.compile(
            "^images/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(?:jpg|png|webp|gif)$");
    private static final Pattern BUNDLED_KEY =
            Pattern.compile("^(?:catalog|community)/[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.svg$");

    private MediaKey() {}

    public static boolean isUserKey(String value) {
        return value != null && USER_KEY.matcher(value).matches();
    }

    public static boolean isBundledKey(String value) {
        return value != null && BUNDLED_KEY.matcher(value).matches();
    }

    /** 저장 키를 same-origin 공개 API 경로로 변환한다. */
    public static String publicPath(String key) {
        if (!isUserKey(key) && !isBundledKey(key)) {
            throw new IllegalArgumentException("untrusted media key");
        }
        return PUBLIC_PATH_PREFIX + key;
    }

    /**
     * 레거시 응답 필드는 신뢰 가능한 same-origin 경로 또는 저장 키만 공개한다.
     * 외부 URL, 경로 순회, 소유권 미확정 값은 빈 값으로 격리한다.
     */
    public static Optional<String> safePublicPath(String storedValue) {
        if (isUserKey(storedValue) || isBundledKey(storedValue)) {
            return Optional.of(publicPath(storedValue));
        }
        if (storedValue != null && storedValue.startsWith(PUBLIC_PATH_PREFIX)) {
            String key = storedValue.substring(PUBLIC_PATH_PREFIX.length());
            if (isUserKey(key) || isBundledKey(key)) {
                return Optional.of(publicPath(key));
            }
        }
        return Optional.empty();
    }

    /** 작성자 수정 화면이 공개 경로를 다시 제출하지 않도록 저장 키만 회수한다. */
    public static Optional<String> safeStoredKey(String storedValue) {
        return safePublicPath(storedValue)
                .map(path -> path.substring(PUBLIC_PATH_PREFIX.length()))
                .filter(MediaKey::isUserKey);
    }

    public static String derivativePrefix(String originalKey) {
        if (!isUserKey(originalKey)) {
            throw new IllegalArgumentException("untrusted original media key");
        }
        return "derivatives/" + originalKey + "/";
    }
}
