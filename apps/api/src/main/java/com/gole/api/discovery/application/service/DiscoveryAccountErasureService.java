package com.gole.api.discovery.application.service;

import com.gole.api.discovery.application.port.in.DiscoveryAccountErasureUseCase;
import com.gole.api.discovery.application.port.out.DiscoveryAccountErasurePort;
import org.springframework.stereotype.Service;

/** 회원 탈퇴 때 찜·팔로우 기록 처리. 판정과 파기는 저장소 포트가 하고, 트랜잭션은 호출자(계정 파기)의 것을 쓴다. */
@Service
public class DiscoveryAccountErasureService implements DiscoveryAccountErasureUseCase {

    private final DiscoveryAccountErasurePort erasure;

    public DiscoveryAccountErasureService(DiscoveryAccountErasurePort erasure) {
        this.erasure = erasure;
    }

    @Override
    public DiscoveryErasure erase(String accountId, String anonymousSubject) {
        return erasure.erase(accountId, anonymousSubject);
    }
}
