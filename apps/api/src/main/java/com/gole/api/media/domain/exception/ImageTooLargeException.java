package com.gole.api.media.domain.exception;

import com.gole.api.common.exception.BadRequestException;

/** 업로드 파일 크기가 한도를 초과할 때. (요구사항 M1.3) */
public class ImageTooLargeException extends BadRequestException {

    public ImageTooLargeException(long maxBytes) {
        super("IMAGE_TOO_LARGE", "Image exceeds the maximum allowed size of " + maxBytes + " bytes");
    }
}
