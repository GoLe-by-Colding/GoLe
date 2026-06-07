package com.gole.api.community.application.port.in;

/**
 * Inbound port: 좋아요. (요구사항 12.4, 12.5)
 */
public interface LikePostUseCase {

    void like(String postId, String userId);
}
