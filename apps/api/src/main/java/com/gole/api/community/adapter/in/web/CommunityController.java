package com.gole.api.community.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.community.adapter.in.web.CommunityDtos.CommentRequest;
import com.gole.api.community.adapter.in.web.CommunityDtos.CommentResponse;
import com.gole.api.community.adapter.in.web.CommunityDtos.EditPostRequest;
import com.gole.api.community.adapter.in.web.CommunityDtos.FeedPageResponse;
import com.gole.api.community.adapter.in.web.CommunityDtos.PostResponse;
import com.gole.api.community.adapter.in.web.CommunityDtos.PublishPostRequest;
import com.gole.api.community.adapter.in.web.CommunityDtos.ReportCommentRequest;
import com.gole.api.community.application.port.in.CommentOnPostUseCase;
import com.gole.api.community.application.port.in.CommentOnPostUseCase.CommentCommand;
import com.gole.api.community.application.port.in.DeleteCommentUseCase;
import com.gole.api.community.application.port.in.DeleteCommentUseCase.DeleteCommentCommand;
import com.gole.api.community.application.port.in.DeletePostUseCase;
import com.gole.api.community.application.port.in.EditPostUseCase;
import com.gole.api.community.application.port.in.EditPostUseCase.EditPostCommand;
import com.gole.api.community.application.port.in.GetFeedUseCase;
import com.gole.api.community.application.port.in.GetFeedUseCase.FeedCursor;
import com.gole.api.community.application.port.in.LikePostUseCase;
import com.gole.api.community.application.port.in.PublishPostUseCase;
import com.gole.api.community.application.port.in.PublishPostUseCase.PublishPostCommand;
import com.gole.api.community.application.port.in.ReportCommentUseCase;
import com.gole.api.community.application.port.in.ReportCommentUseCase.ReportCommentCommand;
import com.gole.api.community.domain.model.PostType;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 커뮤니티 피드/게시글/댓글/좋아요. (요구사항 12)
 */
@Tag(name = "Community", description = "커뮤니티 피드·게시글·댓글·좋아요")
@RestController
@RequestMapping("/api/v1/community/posts")
public class CommunityController {

    private final PublishPostUseCase publishPostUseCase;
    private final CommentOnPostUseCase commentOnPostUseCase;
    private final LikePostUseCase likePostUseCase;
    private final GetFeedUseCase getFeedUseCase;
    private final DeletePostUseCase deletePostUseCase;
    private final DeleteCommentUseCase deleteCommentUseCase;
    private final EditPostUseCase editPostUseCase;
    private final ReportCommentUseCase reportCommentUseCase;

    public CommunityController(
            PublishPostUseCase publishPostUseCase,
            CommentOnPostUseCase commentOnPostUseCase,
            LikePostUseCase likePostUseCase,
            GetFeedUseCase getFeedUseCase,
            DeletePostUseCase deletePostUseCase,
            DeleteCommentUseCase deleteCommentUseCase,
            EditPostUseCase editPostUseCase,
            ReportCommentUseCase reportCommentUseCase) {
        this.publishPostUseCase = publishPostUseCase;
        this.commentOnPostUseCase = commentOnPostUseCase;
        this.likePostUseCase = likePostUseCase;
        this.getFeedUseCase = getFeedUseCase;
        this.deletePostUseCase = deletePostUseCase;
        this.deleteCommentUseCase = deleteCommentUseCase;
        this.editPostUseCase = editPostUseCase;
        this.reportCommentUseCase = reportCommentUseCase;
    }

    @GetMapping
    public List<PostResponse> feed(@RequestParam(defaultValue = "30") int limit, HttpServletRequest http) {
        String viewerId = AuthenticatedUser.optionalId(http).orElse(null);
        return getFeedUseCase.feed(limit).stream()
                .map(post -> PostResponse.from(post, viewerId))
                .toList();
    }

    @GetMapping("/page")
    public FeedPageResponse feedPage(
            @RequestParam(defaultValue = "12") int limit,
            @RequestParam(required = false) String beforeCreatedAt,
            @RequestParam(required = false) String beforeId,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false, name = "q") String query,
            HttpServletRequest http) {
        Optional<FeedCursor> cursor = parseCursor(beforeCreatedAt, beforeId);
        String viewerId = AuthenticatedUser.optionalId(http).orElse(null);
        return FeedPageResponse.from(
                getFeedUseCase.feedPage(limit, cursor, parseTopic(topic), parseQuery(query)), viewerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse publish(@Valid @RequestBody PublishPostRequest request, HttpServletRequest http) {
        String id = publishPostUseCase.publish(new PublishPostCommand(
                AuthenticatedUser.id(http), request.content(), request.imageUrls(), request.topic()));
        return PostResponse.from(getFeedUseCase.getPost(id), AuthenticatedUser.id(http));
    }

    @GetMapping("/{postId}")
    public PostResponse get(@PathVariable String postId, HttpServletRequest http) {
        return PostResponse.from(
                getFeedUseCase.getPost(postId),
                AuthenticatedUser.optionalId(http).orElse(null));
    }

    @PostMapping("/{postId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(@PathVariable String postId, HttpServletRequest http) {
        likePostUseCase.like(postId, AuthenticatedUser.id(http));
    }

    @DeleteMapping("/{postId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(@PathVariable String postId, HttpServletRequest http) {
        likePostUseCase.unlike(postId, AuthenticatedUser.id(http));
    }

    @GetMapping("/{postId}/comments")
    public List<CommentResponse> comments(@PathVariable String postId, @RequestParam(defaultValue = "100") int limit) {
        return getFeedUseCase.comments(postId, limit).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @PostMapping("/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse comment(
            @PathVariable String postId, @Valid @RequestBody CommentRequest request, HttpServletRequest http) {
        return CommentResponse.from(commentOnPostUseCase.comment(
                new CommentCommand(postId, AuthenticatedUser.id(http), request.content())));
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable String postId, @PathVariable String commentId, HttpServletRequest http) {
        deleteCommentUseCase.deleteComment(new DeleteCommentCommand(postId, commentId, AuthenticatedUser.id(http)));
    }

    @PostMapping("/{postId}/comments/{commentId}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> reportComment(
            @PathVariable String postId,
            @PathVariable String commentId,
            @Valid @RequestBody ReportCommentRequest request,
            HttpServletRequest http) {
        String reportId = reportCommentUseCase.report(new ReportCommentCommand(
                AuthenticatedUser.id(http), postId, commentId, request.reason(), request.detail()));
        return Map.of("id", reportId);
    }

    @PutMapping("/{postId}")
    public PostResponse edit(
            @PathVariable String postId, @Valid @RequestBody EditPostRequest request, HttpServletRequest http) {
        editPostUseCase.edit(
                new EditPostCommand(postId, AuthenticatedUser.id(http), request.content(), request.imageUrls()));
        return PostResponse.from(getFeedUseCase.getPost(postId), AuthenticatedUser.id(http));
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String postId, HttpServletRequest http) {
        deletePostUseCase.delete(postId, AuthenticatedUser.id(http));
    }

    private static Optional<FeedCursor> parseCursor(String beforeCreatedAt, String beforeId) {
        boolean hasTime = beforeCreatedAt != null && !beforeCreatedAt.isBlank();
        boolean hasId = beforeId != null && !beforeId.isBlank();
        if (hasTime != hasId) {
            throw new BadRequestException("COMMUNITY_CURSOR_INVALID", "피드 커서가 올바르지 않습니다");
        }
        if (!hasTime) {
            return Optional.empty();
        }
        try {
            return Optional.of(new FeedCursor(Instant.parse(beforeCreatedAt), beforeId));
        } catch (DateTimeParseException exception) {
            throw new BadRequestException("COMMUNITY_CURSOR_INVALID", "피드 커서가 올바르지 않습니다");
        }
    }

    private static Optional<PostType> parseTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return Optional.empty();
        }
        PostType parsed = PostType.findByKey(topic)
                .orElseThrow(() -> new BadRequestException("COMMUNITY_TOPIC_INVALID", "게시글 주제가 올바르지 않습니다"));
        return Optional.of(parsed);
    }

    private static Optional<String> parseQuery(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String normalized = query.strip();
        if (normalized.length() > 100) {
            throw new BadRequestException("COMMUNITY_QUERY_TOO_LONG", "검색어는 100자 이하여야 합니다");
        }
        return Optional.of(normalized);
    }
}
