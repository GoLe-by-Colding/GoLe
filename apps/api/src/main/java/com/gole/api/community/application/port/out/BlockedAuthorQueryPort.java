package com.gole.api.community.application.port.out;

import java.util.List;

/** 사용자 차단 관계 조회 outbound port. 방향은 묻지 않는다. (user-block) */
public interface BlockedAuthorQueryPort {

    /** 두 사람 사이에 차단이 있는가. */
    boolean blockedBetween(String a, String b);

    /** 이 사람과 차단 관계인 상대들(양방향). */
    List<String> blockedCounterparts(String accountId);
}
