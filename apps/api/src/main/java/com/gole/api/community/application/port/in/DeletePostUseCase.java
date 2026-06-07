package com.gole.api.community.application.port.in;

/**
 * Inbound port: 게시글 삭제(작성자). (요구사항 12.7)
 */
public interface DeletePostUseCase {

    void delete(String postId, String requesterId);
}
