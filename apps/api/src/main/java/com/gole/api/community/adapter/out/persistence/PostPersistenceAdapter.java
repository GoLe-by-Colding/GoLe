package com.gole.api.community.adapter.out.persistence;

import com.gole.api.community.application.port.out.PostRepositoryPort;
import com.gole.api.community.application.port.out.PostRepositoryPort.FeedCursor;
import com.gole.api.community.domain.model.Post;
import com.gole.api.community.domain.model.PostStatus;
import com.gole.api.community.domain.model.PostType;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 게시글 영속성 어댑터. 도메인 {@link Post}와 {@link PostDocument}를 양방향 매핑한다.
 */
@Component
public class PostPersistenceAdapter implements PostRepositoryPort {

    private final PostMongoRepository repository;
    private final MongoTemplate mongoTemplate;

    public PostPersistenceAdapter(PostMongoRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Post save(Post post) {
        PostDocument saved = repository.save(toDocument(post));
        return toDomain(saved);
    }

    @Override
    public Optional<Post> findById(String postId) {
        return repository.findById(postId).map(this::toDomain);
    }

    @Override
    public List<Post> findPublishedRecentFirst(int limit) {
        return repository
                .findByStatusOrderByCreatedAtDescIdDesc(PostStatus.PUBLISHED.name(), PageRequest.of(0, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Post> findPublishedPage(
            int limit, Optional<FeedCursor> before, Optional<PostType> topic, Optional<String> searchQuery) {
        List<Criteria> filters = new java.util.ArrayList<>();
        filters.add(Criteria.where("status").is(PostStatus.PUBLISHED.name()));
        topic.ifPresent(value -> filters.add(Criteria.where("type").is(value.name())));
        searchQuery.ifPresent(value -> {
            Pattern safeContains =
                    Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            filters.add(new Criteria()
                    .orOperator(
                            Criteria.where("content").regex(safeContains),
                            Criteria.where("authorId").regex(safeContains)));
        });
        if (before.isPresent()) {
            FeedCursor cursor = before.orElseThrow();
            filters.add(new Criteria()
                    .orOperator(
                            Criteria.where("createdAt").lt(cursor.createdAt()),
                            new Criteria()
                                    .andOperator(
                                            Criteria.where("createdAt").is(cursor.createdAt()),
                                            Criteria.where("_id").lt(cursor.postId()))));
        }

        Criteria criteria = new Criteria().andOperator(filters.toArray(Criteria[]::new));

        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .limit(limit);
        return mongoTemplate.find(query, PostDocument.class).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Post> findPublishedByAuthorIdsRecentFirst(List<String> authorIds, int limit) {
        if (authorIds.isEmpty()) {
            return List.of();
        }
        return repository
                .findByStatusAndAuthorIdInOrderByCreatedAtDesc(
                        PostStatus.PUBLISHED.name(), authorIds, PageRequest.of(0, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private PostDocument toDocument(Post post) {
        return new PostDocument(
                post.getId(),
                post.getAuthorId(),
                post.getContent(),
                post.getImageUrls(),
                post.getType().name(),
                post.getStatus().name(),
                post.getLikedBy(),
                post.getCreatedAt());
    }

    private Post toDomain(PostDocument document) {
        return new Post(
                document.getId(),
                document.getAuthorId(),
                document.getContent(),
                document.getImageUrls(),
                PostType.valueOf(document.getType()),
                PostStatus.valueOf(document.getStatus()),
                document.getLikedBy(),
                document.getCreatedAt());
    }
}
