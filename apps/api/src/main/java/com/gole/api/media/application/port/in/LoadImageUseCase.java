package com.gole.api.media.application.port.in;

/**
 * 이미지 조회 유스케이스(인바운드 포트). (요구사항 M2)
 */
public interface LoadImageUseCase {

    LoadedImage load(String key);

    /**
     * @param content     객체 바이트
     * @param contentType MIME 타입
     */
    record LoadedImage(byte[] content, String contentType) {}
}
