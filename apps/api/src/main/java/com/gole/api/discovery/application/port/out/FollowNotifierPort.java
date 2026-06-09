package com.gole.api.discovery.application.port.out;

/**
 * Outbound port: 셀러 팔로우 알림. 새 팔로워를 셀러에게 알린다(best-effort).
 * 구현 어댑터가 notification 컨텍스트의 인바운드 포트로 위임한다. (알림 후속 트리거)
 */
public interface FollowNotifierPort {

    /** 새 팔로워가 생겼음을 셀러에게 알린다. 실패해도 팔로우 흐름을 막지 않는다. */
    void notifyNewFollower(String sellerId, String followerId);
}
