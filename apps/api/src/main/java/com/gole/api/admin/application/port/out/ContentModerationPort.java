package com.gole.api.admin.application.port.out;

/** Outbound port: 신고된 콘텐츠 조치. 각 콘텐츠를 소유한 컨텍스트가 실제 조치를 한다. */
public interface ContentModerationPort {

    void takedownListing(String listingId, String reason);

    void removePost(String postId, String reason);

    void hideComment(String commentId, String reason);

    void hideReview(String reviewId, String reason);
}
