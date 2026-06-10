package com.gole.api.community.application.service;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.community.application.port.in.CommentOnPostUseCase;
import com.gole.api.community.application.port.in.DeletePostUseCase;
import com.gole.api.community.application.port.in.GetFeedUseCase;
import com.gole.api.community.application.port.in.LikePostUseCase;
import com.gole.api.community.application.port.in.PublishPostUseCase;
import com.gole.api.community.application.port.out.CommentRepositoryPort;
import com.gole.api.community.application.port.out.CommunityIdGeneratorPort;
import com.gole.api.community.application.port.out.PostRepositoryPort;
import com.gole.api.community.application.port.out.PostAuthorNotifierPort;
import com.gole.api.community.domain.exception.PostNotFoundException;
import com.gole.api.community.domain.model.Comment;
import com.gole.api.community.domain.model.Post;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 커뮤니티 유스케이스 구현. inbound port를 구현하고 outbound port에만 의존한다.
 */
@Service
public class CommunityService
        implements PublishPostUseCase,
                CommentOnPostUseCase,
                LikePostUseCase,
                GetFeedUseCase,
                DeletePostUseCase {

    private final PostRepositoryPort postRepository;
    private final CommentRepositoryPort commentRepository;
    private final CommunityIdGeneratorPort idGenerator;
    private final PostAuthorNotifierPort postAuthorNotifier;
    private final Clock clock;

    public CommunityService(
            PostRepositoryPort postRepository,
            CommentRepositoryPort commentRepository,
            CommunityIdGeneratorPort idGenerator,
            PostAuthorNotifierPort postAuthorNotifier,
            Clock clock) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.idGenerator = idGenerator;
        this.postAuthorNotifier = postAuthorNotifier;
        this.clock = clock;
    }

    @Override
    public String publish(PublishPostCommand command) {
        Post post = Post.publish(
                idGenerator.newId(),
                command.authorId(),
                command.content(),
                command.imageUrls(),
                com.gole.api.community.domain.model.PostType.fromKey(command.topic()),
                Instant.now(clock));
        return postRepository.save(post).getId();
    }

    @Override
    public String comment(CommentCommand command) {
        Post post = requirePublished(command.postId());
        Comment comment = new Comment(
                idGenerator.newId(), post.getId(), command.authorId(),
                command.content(), Instant.now(clock));
        String id = commentRepository.save(comment).id();
        // 알림: 내 글에 댓글이 달리면 작성자에게(본인 댓글은 제외, best-effort)
        if (!command.authorId().equals(post.getAuthorId())) {
            postAuthorNotifier.notifyComment(post.getAuthorId(), post.getId());
        }
        return id;
    }

    @Override
    public void like(String postId, String userId) {
        Post post = requirePublished(postId);
        post.like(userId); // 중복 시 예외
        postRepository.save(post);
    }

    @Override
    public List<Post> feed() {
        return postRepository.findPublishedRecentFirst();
    }

    @Override
    public Post getPost(String postId) {
        return requirePublished(postId);
    }

    @Override
    public List<Comment> comments(String postId) {
        return commentRepository.findByPostId(postId);
    }

    @Override
    public void delete(String postId, String requesterId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (!post.getAuthorId().equals(requesterId)) {
            throw new ForbiddenException("NOT_POST_AUTHOR", "Only the author can delete this post");
        }
        post.delete();
        postRepository.save(post);
    }

    private Post requirePublished(String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (!post.isPublished()) {
            throw new PostNotFoundException(postId);
        }
        return post;
    }
}
