package com.gole.api.community.adapter.out.persistence;

import com.gole.api.community.application.port.out.PostRepositoryPort;
import com.gole.api.community.domain.model.Post;
import com.gole.api.community.domain.model.PostStatus;
import com.gole.api.community.domain.model.PostType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 게시글 영속성 어댑터. 도메인 {@link Post}와 {@link PostDocument}를 양방향 매핑한다.
 */
@Component
public class PostPersistenceAdapter implements PostRepositoryPort {

    private final PostMongoRepository repository;

    public PostPersistenceAdapter(PostMongoRepository repository) {
        this.repository = repository;
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
    public List<Post> findPublishedRecentFirst() {
        return repository.findByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED.name()).stream()
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
