package com.gole.api.listing.application.service;

import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.application.port.in.ListListingCommentsUseCase;
import com.gole.api.listing.application.port.in.PostListingCommentUseCase;
import com.gole.api.listing.application.port.out.ListingCommentNotifierPort;
import com.gole.api.listing.application.port.out.ListingCommentRepositoryPort;
import com.gole.api.listing.application.port.out.ListingSellerVerificationPort;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingComment;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 매물 문의 댓글(Q&A). 공개 매물에서만 읽고 쓰며, 저장 뒤 판매자에게 알린다(본인 제외, best-effort). */
@Service
public class ListingCommentService implements ListListingCommentsUseCase, PostListingCommentUseCase {

    /** 한 매물에서 내려주는 댓글 수 상한. */
    static final int LIST_LIMIT = 200;

    private final GetListingUseCase listings;
    private final ListingCommentRepositoryPort comments;
    private final ListingCommentNotifierPort notifier;
    private final ListingSellerVerificationPort sellerVerification;
    private final Clock clock;

    public ListingCommentService(
            GetListingUseCase listings,
            ListingCommentRepositoryPort comments,
            ListingCommentNotifierPort notifier,
            ListingSellerVerificationPort sellerVerification,
            Clock clock) {
        this.listings = listings;
        this.comments = comments;
        this.notifier = notifier;
        this.sellerVerification = sellerVerification;
        this.clock = clock;
    }

    @Override
    public List<ListingComment> list(String listingId) {
        // 숨김·삭제 매물이면 여기서 404 — 그 매물의 댓글은 읽지 않는다.
        listings.getPublicById(listingId);
        return comments.findActiveByListingId(listingId, LIST_LIMIT);
    }

    @Override
    public ListingComment post(PostListingCommentCommand command) {
        Listing listing = listings.getPublicById(command.listingId());
        sellerVerification.requireVerifiedSeller(listing.getSellerId());
        ListingComment saved = comments.save(ListingComment.post(
                UUID.randomUUID().toString(),
                command.listingId(),
                command.authorId(),
                command.content(),
                Instant.now(clock)));
        if (!listing.getSellerId().equals(command.authorId())) {
            try {
                notifier.notifySellerOfQuestion(listing.getSellerId(), listing.getId(), listing.getTitle());
            } catch (RuntimeException ignored) {
                // 어댑터가 이미 흡수하지만, 알림 연계 장애가 댓글 저장을 되돌리지 않게 포트 경계에서 한 번 더 격리한다.
            }
        }
        return saved;
    }
}
