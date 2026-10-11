package com.gole.api.discovery.application.port.out;

import com.gole.api.discovery.application.port.in.DiscoveryAccountErasureUseCase.DiscoveryErasure;

/** Outbound port: 회원 탈퇴 때 찜·팔로우 기록의 차단 판정·파기를 저장소에서 한다. */
public interface DiscoveryAccountErasurePort {

    DiscoveryErasure erase(String accountId, String anonymousSubject);
}
