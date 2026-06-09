package com.gole.api.media.application.port.in;

/**
 * 이미지 조회 유스케이스(인바운드 포트). (요구사항 M2)
 */
public interface LoadImageUseCase {

    LoadedImage load(String key);

    /** 지정 폭으로 축소된 이미지를 조회한다(캐시된 파생물 우선, 불가 시 원본). (백로그 N2a) */
    LoadedImage loadResized(String key, int width);

    /**
     * @param content     객체 바이트
     * @param contentType MIME 타입
     */
    record LoadedImage(byte[] content, String contentType) {}
}
