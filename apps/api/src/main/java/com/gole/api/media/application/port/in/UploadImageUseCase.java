package com.gole.api.media.application.port.in;

import com.gole.api.media.domain.model.StoredImage;

/**
 * 이미지 업로드 유스케이스(인바운드 포트). (요구사항 M1)
 */
public interface UploadImageUseCase {

    StoredImage upload(UploadImageCommand command);

    /**
     * @param content         업로드 바이트
     * @param contentType     MIME 타입 (예: image/jpeg)
     * @param originalFilename 원본 파일명(신뢰하지 않음, 로깅/확장자 힌트용)
     */
    record UploadImageCommand(byte[] content, String contentType, String originalFilename) {}
}
