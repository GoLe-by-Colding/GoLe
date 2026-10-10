package com.gole.api.parts.application.service;

import com.gole.api.parts.application.port.in.PartsAccountErasureUseCase;
import com.gole.api.parts.application.port.out.PartsAccountErasurePort;
import org.springframework.stereotype.Service;

/** 회원 탈퇴 때 부품 요청 기록 처리. 판정과 파기는 저장소 포트가 하고, 트랜잭션은 호출자(계정 파기)의 것을 쓴다. */
@Service
public class PartsAccountErasureService implements PartsAccountErasureUseCase {

    private final PartsAccountErasurePort erasure;

    public PartsAccountErasureService(PartsAccountErasurePort erasure) {
        this.erasure = erasure;
    }

    @Override
    public PartsErasure erase(String accountId, String anonymousSubject) {
        return erasure.erase(accountId, anonymousSubject);
    }
}
