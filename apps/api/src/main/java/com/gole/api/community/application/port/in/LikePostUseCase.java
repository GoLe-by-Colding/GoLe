package com.gole.api.community.application.port.in;

/**
 * Inbound port: 좋아요. (요구사항 12.4, 12.5)
 */
public interface LikePostUseCase {

    void like(String postId, String userId);

    /** DELETE 재시도에도 안전한 좋아요 취소. */
    void unlike(String postId, String userId);
}
