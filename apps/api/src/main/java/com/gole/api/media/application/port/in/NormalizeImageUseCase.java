package com.gole.api.media.application.port.in;

/** 저장 없이 입력 사진을 검증하고 메타데이터 없는 래스터로 정규화한다. */
public interface NormalizeImageUseCase {
    NormalizedImage normalize(byte[] source);

    record NormalizedImage(byte[] content, String contentType, int width, int height) {}
}
