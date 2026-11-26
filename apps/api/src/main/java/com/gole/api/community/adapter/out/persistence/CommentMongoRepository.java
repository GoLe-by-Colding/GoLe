package com.gole.api.community.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 댓글 Spring Data MongoDB 리포지토리.
 */
public interface CommentMongoRepository extends MongoRepository<CommentDocument, String> {

    /** 특정 게시글의 댓글을 제외 작성자 없이 작성 순(오래된→최신)으로 조회한다. */
    List<CommentDocument> findByPostIdAndHiddenAtIsNullAndAuthorIdNotInOrderByCreatedAtAsc(
            String postId, Collection<String> excludedAuthorIds, Pageable pageable);
}
