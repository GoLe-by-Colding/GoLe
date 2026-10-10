package com.gole.api.listing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ListingCommentMongoRepository extends MongoRepository<ListingCommentDocument, String> {

    /** 삭제되지 않은 댓글. 정렬·상한은 {@link Pageable} 로 받는다(어댑터가 createdAt 오름차순으로 건다). */
    List<ListingCommentDocument> findByListingIdAndDeletedFalse(String listingId, Pageable pageable);
}
