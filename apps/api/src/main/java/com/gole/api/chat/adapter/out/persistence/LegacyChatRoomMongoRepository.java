package com.gole.api.chat.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/** 레거시 매물 방 읽기 전용 저장소. */
public interface LegacyChatRoomMongoRepository extends MongoRepository<LegacyChatRoomView, String> {

    @Query("{ $or: [ { 'buyerId': ?0 }, { 'sellerId': ?0 } ] }")
    List<LegacyChatRoomView> findMine(String accountId, Pageable pageable);

    Optional<LegacyChatRoomView> findById(String id);
}
