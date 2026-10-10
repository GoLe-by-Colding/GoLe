package com.gole.api.collection.application.service;

import com.gole.api.collection.application.port.in.CollectionAccountErasureUseCase;
import com.gole.api.collection.application.port.out.CollectionAccountErasurePort;
import org.springframework.stereotype.Service;

/** 회원 탈퇴 때 컬렉션 기록 처리. 판정과 파기는 저장소 포트가 하고, 트랜잭션은 호출자(계정 파기)의 것을 쓴다. */
@Service
public class CollectionAccountErasureService implements CollectionAccountErasureUseCase {

    private final CollectionAccountErasurePort erasure;

    public CollectionAccountErasureService(CollectionAccountErasurePort erasure) {
        this.erasure = erasure;
    }

    @Override
    public CollectionErasure erase(String accountId, String anonymousSubject) {
        return erasure.erase(accountId, anonymousSubject);
    }
}
