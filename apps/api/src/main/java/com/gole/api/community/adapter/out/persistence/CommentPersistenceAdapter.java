package com.gole.api.community.adapter.out.persistence;

import com.gole.api.community.application.port.out.CommentRepositoryPort;
import com.gole.api.community.domain.model.Comment;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 댓글 영속성 어댑터. 도메인 {@link Comment}와 {@link CommentDocument}를 양방향 매핑한다.
 */
@Component
public class CommentPersistenceAdapter implements CommentRepositoryPort {

    private final CommentMongoRepository repository;

    public CommentPersistenceAdapter(CommentMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Comment save(Comment comment) {
        CommentDocument saved = repository.save(toDocument(comment));
        return toDomain(saved);
    }

    @Override
    public List<Comment> findByPostId(String postId) {
        return repository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(this::toDomain)
                .toList();
    }

    private CommentDocument toDocument(Comment comment) {
        return new CommentDocument(
                comment.id(), comment.postId(), comment.authorId(), comment.content(), comment.createdAt());
    }

    private Comment toDomain(CommentDocument document) {
        return new Comment(
                document.getId(),
                document.getPostId(),
                document.getAuthorId(),
                document.getContent(),
                document.getCreatedAt());
    }
}
