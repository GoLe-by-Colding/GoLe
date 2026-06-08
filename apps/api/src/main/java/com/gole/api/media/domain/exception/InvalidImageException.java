package com.gole.api.media.domain.exception;

import com.gole.api.common.exception.BadRequestException;

/** 업로드 파일이 비었거나 이미지가 아닐 때. (요구사항 M1.2) */
public class InvalidImageException extends BadRequestException {

    public InvalidImageException(String message) {
        super("INVALID_IMAGE", message);
    }
}
