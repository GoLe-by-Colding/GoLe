package com.gole.api.listing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ListingCommentMongoRepository extends MongoRepository<ListingCommentDocument, String> {

    List<ListingCommentDocument> findByListingIdAndDeletedFalseOrderByCreatedAtAsc(String listingId);
}
