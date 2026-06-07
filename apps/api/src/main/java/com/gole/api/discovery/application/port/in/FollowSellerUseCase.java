package com.gole.api.discovery.application.port.in;

import java.util.List;

/**
 * Inbound port: 셀러 팔로우/언팔로우. (요구사항 16.3, 16.4, 16.5)
 */
public interface FollowSellerUseCase {

    void follow(String userId, String sellerId);

    void unfollow(String userId, String sellerId);

    List<String> following(String userId);
}
