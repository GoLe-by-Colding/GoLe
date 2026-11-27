package com.gole.api.discovery.application.port.out;

/** Outbound port: 두 사람 사이에 차단이 있는지 묻는다. 팔로우는 차단 중에 만들 수 없다. */
public interface FollowBlockCheckPort {

    /** 방향을 묻지 않는다. */
    boolean blockedBetween(String a, String b);
}
