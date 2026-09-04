package com.gole.api.media.application.port.in;

import java.util.Optional;

/** 객체 스토리지 접근 전 사용자 미디어 공개 여부를 판정한다. */
public interface AuthorizeMediaReadUseCase {

    void requireReadable(String key, Optional<String> viewerId);
}
