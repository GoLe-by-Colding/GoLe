package com.gole.api.community.application.service;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.community.application.port.in.CommentOnPostUseCase;
import com.gole.api.community.application.port.in.DeleteCommentUseCase;
import com.gole.api.community.application.port.in.DeleteCommentUseCase.DeleteCommentCommand;
import com.gole.api.community.application.port.in.DeletePostUseCase;
import com.gole.api.community.application.port.in.EditPostUseCase;
import com.gole.api.community.application.port.in.GetFeedUseCase;
import com.gole.api.community.application.port.in.GetFeedUseCase.FeedCursor;
import com.gole.api.community.application.port.in.GetFeedUseCase.FeedPage;
import com.gole.api.community.application.port.in.LikePostUseCase;
import com.gole.api.community.application.port.in.ModerateCommentUseCase;
import com.gole.api.community.application.port.in.ModeratePostUseCase;
import com.gole.api.community.application.port.in.PatchPostUseCase;
import com.gole.api.community.application.port.in.PublishPostUseCase;
import com.gole.api.community.application.port.out.CommentRepositoryPort;
import com.gole.api.community.application.port.out.CommunityIdGeneratorPort;
import com.gole.api.community.application.port.out.PostAuthorNotifierPort;
import com.gole.api.community.application.port.out.PostRepositoryPort;
import com.gole.api.community.domain.exception.PostNotFoundException;
import com.gole.api.community.domain.model.Comment;
import com.gole.api.community.domain.model.Post;
import com.gole.api.community.domain.model.PostStatus;
import com.gole.api.community.domain.model.PostType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
                DeletePostUseCase,
                DeleteCommentUseCase,
                EditPostUseCase,
                PatchPostUseCase,
                ModeratePostUseCase,
                ModerateCommentUseCase {

    private static final int MAX_FEED_ROWS = 100;
    private static final int MAX_FEED_PAGE_ROWS = 50;
    private static final int MAX_COMMENT_ROWS = 200;

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
    public Comment comment(CommentCommand command) {
        Post post = requirePublished(command.postId());
        Comment comment = new Comment(
                idGenerator.newId(), post.getId(), command.authorId(), command.content(), Instant.now(clock));
        Comment saved = commentRepository.save(comment);
        // 알림: 내 글에 댓글이 달리면 작성자에게(본인 댓글은 제외, best-effort)
        if (!command.authorId().equals(post.getAuthorId())) {
            postAuthorNotifier.notifyComment(post.getAuthorId(), post.getId());
        }
        return saved;
    }

    @Override
    public void like(String postId, String userId) {
        Post post = requirePublished(postId);
        post.like(userId); // 중복 시 예외
        postRepository.save(post);
        if (!userId.equals(post.getAuthorId())) {
            postAuthorNotifier.notifyLike(post.getAuthorId(), post.getId(), userId);
        }
    }

    @Override
    public void unlike(String postId, String userId) {
        Post post = requirePublished(postId);
        post.unlike(userId);
        postRepository.save(post);
    }

    @Override
    public List<Post> feed(int limit) {
        return postRepository.findPublishedRecentFirst(clamp(limit, MAX_FEED_ROWS));
    }

    @Override
    public FeedPage feedPage(
            int requestedLimit, Optional<FeedCursor> before, Optional<PostType> topic, Optional<String> query) {
        int limit = clamp(requestedLimit, MAX_FEED_PAGE_ROWS);
        Optional<PostRepositoryPort.FeedCursor> repositoryCursor =
                before.map(cursor -> new PostRepositoryPort.FeedCursor(cursor.createdAt(), cursor.postId()));
        Optional<String> normalizedQuery = query.map(String::strip).filter(value -> !value.isEmpty());
        List<Post> fetched = postRepository.findPublishedPage(limit + 1, repositoryCursor, topic, normalizedQuery);
        boolean hasMore = fetched.size() > limit;
        List<Post> items = fetched.subList(0, Math.min(limit, fetched.size()));
        return new FeedPage(List.copyOf(items), hasMore);
    }

    @Override
    public Post getPost(String postId) {
        return requirePublished(postId);
    }

    @Override
    public List<Comment> comments(String postId, int limit) {
        requirePublished(postId);
        return commentRepository.findByPostId(postId, clamp(limit, MAX_COMMENT_ROWS));
    }

    @Override
    public Comment getForModeration(String commentId) {
        return commentRepository
                .findById(commentId)
                .orElseThrow(() ->
                        new com.gole.api.common.exception.NotFoundException("COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다"));
    }

    @Override
    public void deleteComment(DeleteCommentCommand command) {
        Comment comment = commentRepository
                .findById(command.commentId())
                .filter(candidate -> candidate.postId().equals(command.postId()))
                .filter(candidate -> !candidate.isHidden())
                .orElseThrow(() ->
                        new com.gole.api.common.exception.NotFoundException("COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다"));
        if (!comment.authorId().equals(command.requesterId())) {
            throw new ForbiddenException("NOT_COMMENT_AUTHOR", "본인이 작성한 댓글만 삭제할 수 있습니다");
        }
        commentRepository.save(comment.hide("작성자가 삭제함", Instant.now(clock)));
    }

    @Override
    public void hide(String commentId, String reason) {
        Comment comment = getForModeration(commentId);
        commentRepository.save(comment.hide(reason, Instant.now(clock)));
    }

    @Override
    public void delete(String postId, String requesterId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new PostNotFoundException(postId));
        if (!post.getAuthorId().equals(requesterId)) {
            throw new ForbiddenException("NOT_POST_AUTHOR", "Only the author can delete this post");
        }
        post.delete();
        postRepository.save(post);
    }

    /**
     * 운영자 강제 삭제. 작성자 검증을 생략하는 것이 이 유스케이스의 존재 이유다.
     * 사유는 관리자 컨텍스트의 감사 로그가 보관한다. (admin-console 요구사항 5.2)
     */
    @Override
    public void removeByModerator(String postId, String reason) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new PostNotFoundException(postId));
        post.delete();
        postRepository.save(post);
    }

    @Override
    public void edit(EditPostCommand command) {
        Post post = requirePublished(command.postId());
        if (!post.getAuthorId().equals(command.requesterId())) {
            throw new ForbiddenException("NOT_POST_AUTHOR", "Only the author can edit this post");
        }
        post.edit(command.content(), command.imageUrls());
        postRepository.save(post);
    }

    @Override
    public Post patch(PatchPostCommand command) {
        Post post = postRepository
                .findById(command.postId())
                .filter(candidate -> candidate.getStatus() != PostStatus.DELETED)
                .orElseThrow(() -> new PostNotFoundException(command.postId()));
        if (!post.getAuthorId().equals(command.requesterId())) {
            throw new ForbiddenException("NOT_POST_AUTHOR", "Only the author can edit this post");
        }

        String nextBody = command.body().provided() ? command.body().value() : post.getContent();
        List<String> nextPhotos = command.photos().provided() ? command.photos().value() : post.getImageUrls();
        PostStatus nextStatus = command.status().provided() ? command.status().value() : post.getStatus();
        post.edit(nextBody, nextPhotos, nextStatus);
        return postRepository.save(post);
    }

    private Post requirePublished(String postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new PostNotFoundException(postId));
        if (!post.isPublished()) {
            throw new PostNotFoundException(postId);
        }
        return post;
    }

    private static int clamp(int requested, int maximum) {
        return Math.max(1, Math.min(requested, maximum));
    }
}
