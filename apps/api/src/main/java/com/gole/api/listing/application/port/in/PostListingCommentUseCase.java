package com.gole.api.listing.application.port.in;

import com.gole.api.listing.domain.model.ListingComment;

/**
 * Inbound port: 공개 매물에 문의 댓글을 남긴다.
 *
 * <p>판매자 신원확인이 안 된 매물에는 새 문의를 받지 않는다(저장 전에 거부). 저장 뒤 판매자에게 알린다(본인 댓글 제외,
 * best-effort — 알림 실패는 저장을 되돌리지 않는다).
 */
public interface PostListingCommentUseCase {

    ListingComment post(PostListingCommentCommand command);

    /** @param authorId 서버가 검증한 세션 계정. 요청 본문의 작성자 값은 쓰지 않는다. */
    record PostListingCommentCommand(String listingId, String authorId, String content) {}
}
