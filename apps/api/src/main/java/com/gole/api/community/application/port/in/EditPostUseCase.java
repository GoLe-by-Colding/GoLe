package com.gole.api.community.application.port.in;

import java.util.List;

/**
 * Inbound port: 게시글 본문/이미지 수정(작성자 본인만).
 */
public interface EditPostUseCase {

    void edit(EditPostCommand command);

    record EditPostCommand(String postId, String requesterId, String content, List<String> imageKeys) {}
}
