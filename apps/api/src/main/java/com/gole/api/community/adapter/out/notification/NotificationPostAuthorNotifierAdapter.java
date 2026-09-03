package com.gole.api.community.adapter.out.notification;

import com.gole.api.community.application.port.out.PostAuthorNotifierPort;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 게시글 작성자 알림 어댑터. notification 인바운드 포트({@link NotifyUseCase})로 위임한다.
 * 알림 실패는 흡수해 댓글 흐름을 막지 않는다(best-effort).
 */
@Component
public class NotificationPostAuthorNotifierAdapter implements PostAuthorNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationPostAuthorNotifierAdapter.class);

    private final NotifyUseCase notifyUseCase;

    public NotificationPostAuthorNotifierAdapter(NotifyUseCase notifyUseCase) {
        this.notifyUseCase = notifyUseCase;
    }

    @Override
    public void notifyComment(String authorId, String postId) {
        try {
            notifyUseCase.notify(
                    new NotifyCommand(authorId, NotificationType.COMMENT, "내 글에 새 댓글이 달렸어요", "/community/" + postId));
        } catch (RuntimeException e) {
            log.warn("댓글 알림 발송 실패 authorId={} postId={}: {}", authorId, postId, e.getMessage());
        }
    }

    @Override
    public void notifyLike(String authorId, String postId, String actorId) {
        try {
            notifyUseCase.notify(new NotifyCommand(
                    authorId,
                    NotificationType.POST_LIKED,
                    "누군가 내 글을 좋아해요",
                    "/community/" + postId,
                    "community-like:" + postId + ":" + actorId));
        } catch (RuntimeException e) {
            log.warn("좋아요 알림 발송 실패 authorId={} postId={} actorId={}: {}", authorId, postId, actorId, e.getMessage());
        }
    }
}
