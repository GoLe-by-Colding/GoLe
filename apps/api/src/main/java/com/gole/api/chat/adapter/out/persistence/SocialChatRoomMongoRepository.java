package com.gole.api.chat.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SocialChatRoomMongoRepository extends MongoRepository<SocialChatRoomDocument, String> {

    Optional<SocialChatRoomDocument> findByDedupeKey(String dedupeKey);

    List<SocialChatRoomDocument> findByMemberIdsContaining(String accountId, Pageable pageable);
}
