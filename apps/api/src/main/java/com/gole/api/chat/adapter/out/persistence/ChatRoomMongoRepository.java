package com.gole.api.chat.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatRoomMongoRepository extends MongoRepository<ChatRoomDocument, String> {

    Optional<ChatRoomDocument> findByBuyerIdAndSellerIdAndListingId(String buyerId, String sellerId, String listingId);

    List<ChatRoomDocument> findTop100ByBuyerIdOrSellerIdOrderByLastMessageAtDesc(String buyerId, String sellerId);
}
