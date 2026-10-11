package com.gole.api.admin.adapter.out.moderation;

import com.gole.api.admin.application.port.out.ContentModerationPort;
import com.gole.api.community.application.port.in.ModerateCommentUseCase;
import com.gole.api.community.application.port.in.ModeratePostUseCase;
import com.gole.api.listing.application.port.in.ModerateListingUseCase;
import com.gole.api.review.application.port.in.ModerateReviewUseCase;
import org.springframework.stereotype.Component;

/** 콘텐츠 조치 통합 어댑터. 매물은 listing, 게시글·댓글은 community, 후기는 review 의 조치 유스케이스에 맡긴다. */
@Component
public class ContentModerationAdapter implements ContentModerationPort {

    private final ModerateListingUseCase listings;
    private final ModeratePostUseCase posts;
    private final ModerateCommentUseCase comments;
    private final ModerateReviewUseCase reviews;

    public ContentModerationAdapter(
            ModerateListingUseCase listings,
            ModeratePostUseCase posts,
            ModerateCommentUseCase comments,
            ModerateReviewUseCase reviews) {
        this.listings = listings;
        this.posts = posts;
        this.comments = comments;
        this.reviews = reviews;
    }

    @Override
    public void takedownListing(String listingId, String reason) {
        listings.takedown(listingId, reason);
    }

    @Override
    public void removePost(String postId, String reason) {
        posts.removeByModerator(postId, reason);
    }

    @Override
    public void hideComment(String commentId, String reason) {
        comments.hide(commentId, reason);
    }

    @Override
    public void hideReview(String reviewId, String reason) {
        reviews.hide(reviewId, reason);
    }
}
