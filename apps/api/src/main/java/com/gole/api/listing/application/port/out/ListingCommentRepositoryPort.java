package com.gole.api.listing.application.port.out;

import com.gole.api.listing.domain.model.ListingComment;
import java.util.List;

/** Outbound port: 매물 문의 댓글 저장소. */
public interface ListingCommentRepositoryPort {

    ListingComment save(ListingComment comment);

    /** 삭제되지 않은 댓글을 오래된 순으로 최대 {@code limit} 건. */
    List<ListingComment> findActiveByListingId(String listingId, int limit);
}
