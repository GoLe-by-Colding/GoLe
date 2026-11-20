package com.gole.api.media.domain.exception;

import com.gole.api.common.exception.BadRequestException;

/** 업로드 파일 크기가 한도를 초과할 때. (요구사항 M1.3) */
public class ImageTooLargeException extends BadRequestException {

    public ImageTooLargeException(long maxBytes) {
        super("IMAGE_TOO_LARGE", "이미지가 너무 큽니다. " + Math.max(1, maxBytes / (1024 * 1024)) + "MB 이하로 올려 주세요");
    }
}
