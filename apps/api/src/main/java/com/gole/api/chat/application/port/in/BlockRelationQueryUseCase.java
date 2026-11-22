package com.gole.api.chat.application.port.in;

import java.util.List;

/** Inbound port: 다른 컨텍스트(커뮤니티·팔로우)가 쓰는 차단 관계 조회. (user-block D1) */
public interface BlockRelationQueryUseCase {

    /** 두 사람 사이에 차단이 있는가. 방향은 묻지 않는다. */
    boolean blockedBetween(String a, String b);

    /** 이 사람과 차단 관계인 상대들(양방향, 중복 없음). */
    List<String> blockedCounterparts(String accountId);
}
