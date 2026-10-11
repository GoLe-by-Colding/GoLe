package com.gole.api.community.application.port.out;

import com.gole.api.community.application.port.in.CommunityAccountErasureUseCase.CommunityErasure;

/** Outbound port: 회원 탈퇴 때 커뮤니티 기록의 차단 판정·파기를 저장소에서 한다. */
public interface CommunityAccountErasurePort {

    boolean hasPublicContent(String accountId);

    CommunityErasure erase(String accountId, String anonymousSubject);
}
