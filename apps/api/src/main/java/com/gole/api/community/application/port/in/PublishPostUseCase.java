package com.gole.api.community.application.port.in;

import java.util.List;

/**
 * Inbound port: 게시글 작성. (요구사항 12.1, 12.2)
 */
public interface PublishPostUseCase {

    String publish(PublishPostCommand command);

    /** topic: 주제 키(general/showcase/moc/review/question/tip/easter_egg). null이면 자유(general). */
    record PublishPostCommand(String authorId, String content, List<String> imageUrls, String topic) {}
}
