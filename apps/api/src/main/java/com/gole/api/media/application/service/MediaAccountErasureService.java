package com.gole.api.media.application.service;

import com.gole.api.media.application.port.in.MediaAccountErasureUseCase;
import com.gole.api.media.application.port.out.MediaAccountErasurePort;
import org.springframework.stereotype.Service;

/** 회원 탈퇴 때 미디어 기록 처리. 판정과 파기는 저장소 포트가 하고, 트랜잭션은 호출자(계정 파기)의 것을 쓴다. */
@Service
public class MediaAccountErasureService implements MediaAccountErasureUseCase {

    private final MediaAccountErasurePort erasure;

    public MediaAccountErasureService(MediaAccountErasurePort erasure) {
        this.erasure = erasure;
    }

    @Override
    public boolean hasLiveMedia(String accountId) {
        return erasure.hasLiveMedia(accountId);
    }

    @Override
    public MediaErasure erase(String accountId, String anonymousSubject) {
        return erasure.erase(accountId, anonymousSubject);
    }
}
