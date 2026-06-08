package com.gole.api.media.domain.model;

import java.util.Objects;

/**
 * 저장된 이미지 객체의 메타데이터 값 객체. (요구사항 M1)
 *
 * @param key         객체 스토리지 키 (예: {@code images/<uuid>.jpg})
 * @param url         공개 조회 URL (백엔드 스트리밍 경유)
 * @param contentType MIME 타입 (예: {@code image/jpeg})
 * @param size        바이트 크기
 */
public record StoredImage(String key, String url, String contentType, long size) {

    public StoredImage {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(contentType, "contentType");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }
}
