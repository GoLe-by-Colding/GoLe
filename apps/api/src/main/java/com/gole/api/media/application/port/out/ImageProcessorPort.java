package com.gole.api.media.application.port.out;

import java.util.Optional;

/**
 * 이미지 변환(리사이즈) 아웃바운드 포트. 구현(JDK ImageIO 등)은 어댑터가 담당한다.
 * (이미지 썸네일 — 백로그 N2a)
 */
public interface ImageProcessorPort {

    /**
     * 원본을 targetWidth로 축소한다(가로세로 비율 유지).
     *
     * @return 축소된 바이트. 디코딩 불가(미지원 포맷 등)이거나 축소가 불필요(원본이 더 작음)하면 빈 Optional.
     */
    Optional<byte[]> resizeToWidth(byte[] source, int targetWidth, String contentType);
}
