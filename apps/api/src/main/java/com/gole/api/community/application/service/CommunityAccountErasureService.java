package com.gole.api.community.application.service;

import com.gole.api.community.application.port.in.CommunityAccountErasureUseCase;
import com.gole.api.community.application.port.out.CommunityAccountErasurePort;
import org.springframework.stereotype.Service;

/** 회원 탈퇴 때 커뮤니티 기록 처리. 판정과 파기는 저장소 포트가 하고, 트랜잭션은 호출자(계정 파기)의 것을 쓴다. */
@Service
public class CommunityAccountErasureService implements CommunityAccountErasureUseCase {

    private final CommunityAccountErasurePort erasure;

    public CommunityAccountErasureService(CommunityAccountErasurePort erasure) {
        this.erasure = erasure;
    }

    @Override
    public boolean hasPublicContent(String accountId) {
        return erasure.hasPublicContent(accountId);
    }

    @Override
    public CommunityErasure erase(String accountId, String anonymousSubject) {
        return erasure.erase(accountId, anonymousSubject);
    }
}
