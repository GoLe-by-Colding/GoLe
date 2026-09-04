package com.gole.api.media.domain.exception;

import com.gole.api.common.exception.BadRequestException;

/** 외부 URL·타인 자산·만료 자산을 같은 오류로 감춰 소유권 탐색을 막는다. */
public final class InvalidMediaReferenceException extends BadRequestException {

    public InvalidMediaReferenceException() {
        super("MEDIA_ASSET_NOT_ATTACHABLE", "업로드한 본인 미디어만 한 번 연결할 수 있습니다");
    }
}
