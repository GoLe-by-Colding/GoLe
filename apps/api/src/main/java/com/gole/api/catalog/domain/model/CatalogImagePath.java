package com.gole.api.catalog.domain.model;

import com.gole.api.common.exception.BadRequestException;
import java.util.regex.Pattern;

/** 카탈로그가 저장·노출할 수 있는 same-origin 번들 이미지 경계. */
public final class CatalogImagePath {

    public static final String REGEXP = "^(?:|/api/v1/media/catalog/[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.svg)$";

    private static final Pattern SAFE_PATH = Pattern.compile(REGEXP);

    private CatalogImagePath() {}

    /** 관리자 입력은 외부 URL이나 다른 미디어 namespace이면 명시적으로 거부한다. */
    public static String requireSafeInput(String value) {
        String normalized = normalize(value);
        if (normalized != null && !SAFE_PATH.matcher(normalized).matches()) {
            throw new BadRequestException("INVALID_CATALOG_IMAGE", "카탈로그 이미지는 등록된 내부 catalog SVG 경로만 사용할 수 있습니다");
        }
        return normalized;
    }

    /** 기존 DB의 외부/비정규 값은 응답에 전달하지 않고 null로 격리한다. */
    public static String quarantineUnsafeStoredValue(String value) {
        String normalized = normalize(value);
        return normalized != null && SAFE_PATH.matcher(normalized).matches() ? normalized : null;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
