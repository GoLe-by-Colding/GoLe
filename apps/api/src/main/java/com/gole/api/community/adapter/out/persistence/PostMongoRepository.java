package com.gole.api.community.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 게시글 Spring Data MongoDB 리포지토리.
 */
public interface PostMongoRepository extends MongoRepository<PostDocument, String> {

    /** 특정 상태의 게시글을 최신→오래된 순으로 조회한다. (피드) */
    List<PostDocument> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}
