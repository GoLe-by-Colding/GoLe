package com.gole.api.media.application.port.out;

import com.gole.api.media.application.port.in.MediaAccountErasureUseCase.MediaErasure;

/** Outbound port: 회원 탈퇴 때 미디어 기록의 차단 판정·파기를 저장소에서 한다. */
public interface MediaAccountErasurePort {

    boolean hasLiveMedia(String accountId);

    MediaErasure erase(String accountId, String anonymousSubject);
}
