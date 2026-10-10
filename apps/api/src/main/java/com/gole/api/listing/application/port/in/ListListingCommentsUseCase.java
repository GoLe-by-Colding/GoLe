package com.gole.api.listing.application.port.in;

import com.gole.api.listing.domain.model.ListingComment;
import java.util.List;

/** Inbound port: 공개 매물의 문의 댓글 목록(오래된 순, 최대 200건). 숨김·삭제 매물이면 목록을 읽지 않고 404. */
public interface ListListingCommentsUseCase {

    List<ListingComment> list(String listingId);
}
