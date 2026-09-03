package com.gole.api.community.application.port.out;

/**
 * Outbound port: 게시글 작성자 알림. 댓글 등 활동을 작성자에게 알린다(best-effort).
 * 구현 어댑터가 notification 컨텍스트의 인바운드 포트로 위임한다. (알림 후속 트리거)
 */
public interface PostAuthorNotifierPort {

    /** 내 글에 댓글이 달렸음을 작성자에게 알린다. 실패해도 댓글 흐름을 막지 않는다. */
    void notifyComment(String authorId, String postId);

    /** 내 글에 다른 사용자가 좋아요를 눌렀음을 한 사용자·게시글당 한 번 알린다. */
    default void notifyLike(String authorId, String postId, String actorId) {}
}
