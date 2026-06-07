package com.gole.api.community.application.port.in;

import java.util.List;

/**
 * Inbound port: 게시글 작성. (요구사항 12.1, 12.2)
 */
public interface PublishPostUseCase {

    String publish(PublishPostCommand command);

    record PublishPostCommand(String authorId, String content, List<String> imageUrls, boolean moc) {
    }
}
