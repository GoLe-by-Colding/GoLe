package com.gole.api.media.application.port.out;

import java.util.Optional;

/**
 * 이미지 변환(리사이즈) 아웃바운드 포트. 구현(JDK ImageIO 등)은 어댑터가 담당한다.
 * (이미지 썸네일 — 백로그 N2a)
 */
public interface ImageProcessorPort {

    /**
     * 업로드 이미지를 픽셀로 디코딩한 뒤 메타데이터 없는 허용 포맷으로 다시 인코딩한다.
     *
     * <p>구현체는 저장 전에 크기/총 픽셀 상한을 검사해야 하며 EXIF, GPS, ICC, 코멘트와 애니메이션을
     * 결과에 전달해서는 안 된다.
     */
    SanitizedImage sanitizeForStorage(byte[] source, String contentType);

    /**
     * 원본을 targetWidth로 축소한다(가로세로 비율 유지).
     *
     * @return 축소된 바이트. 디코딩 불가(미지원 포맷 등)이거나 축소가 불필요(원본이 더 작음)하면 빈 Optional.
     */
    Optional<byte[]> resizeToWidth(byte[] source, int targetWidth, String contentType);

    record SanitizedImage(byte[] content, String contentType, int width, int height) {}
}
