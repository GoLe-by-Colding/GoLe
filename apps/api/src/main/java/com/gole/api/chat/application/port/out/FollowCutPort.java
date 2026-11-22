package com.gole.api.chat.application.port.out;

/** Outbound port: 차단하면 두 사람의 팔로우를 양방향으로 끊는다. */
public interface FollowCutPort {

    /** a→b, b→a 팔로우를 모두 지운다. 없으면 아무 일도 하지 않는다(멱등). */
    void cutBoth(String a, String b);
}
