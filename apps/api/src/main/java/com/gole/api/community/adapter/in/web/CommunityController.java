package com.gole.api.community.adapter.in.web;

import com.gole.api.community.adapter.in.web.CommunityDtos.CommentRequest;
import com.gole.api.community.adapter.in.web.CommunityDtos.CommentResponse;
import com.gole.api.community.adapter.in.web.CommunityDtos.EditPostRequest;
import com.gole.api.community.adapter.in.web.CommunityDtos.LikeRequest;
import com.gole.api.community.adapter.in.web.CommunityDtos.PostResponse;
import com.gole.api.community.adapter.in.web.CommunityDtos.PublishPostRequest;
import com.gole.api.community.application.port.in.CommentOnPostUseCase;
import com.gole.api.community.application.port.in.CommentOnPostUseCase.CommentCommand;
import com.gole.api.community.application.port.in.DeletePostUseCase;
import com.gole.api.community.application.port.in.EditPostUseCase;
import com.gole.api.community.application.port.in.EditPostUseCase.EditPostCommand;
import com.gole.api.community.application.port.in.GetFeedUseCase;
import com.gole.api.community.application.port.in.LikePostUseCase;
import com.gole.api.community.application.port.in.PublishPostUseCase;
import com.gole.api.community.application.port.in.PublishPostUseCase.PublishPostCommand;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
    private final EditPostUseCase editPostUseCase;

    public CommunityController(
            PublishPostUseCase publishPostUseCase,
            CommentOnPostUseCase commentOnPostUseCase,
            LikePostUseCase likePostUseCase,
            GetFeedUseCase getFeedUseCase,
            DeletePostUseCase deletePostUseCase,
            EditPostUseCase editPostUseCase) {
        this.publishPostUseCase = publishPostUseCase;
        this.commentOnPostUseCase = commentOnPostUseCase;
        this.likePostUseCase = likePostUseCase;
        this.getFeedUseCase = getFeedUseCase;
        this.deletePostUseCase = deletePostUseCase;
        this.editPostUseCase = editPostUseCase;
    }

    @GetMapping
    public List<PostResponse> feed() {
        return getFeedUseCase.feed().stream().map(PostResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse publish(@Valid @RequestBody PublishPostRequest request) {
        String id = publishPostUseCase.publish(
                new PublishPostCommand(request.authorId(), request.content(), request.imageUrls(), request.topic()));
        return PostResponse.from(getFeedUseCase.getPost(id));
    }

    @GetMapping("/{postId}")
    public PostResponse get(@PathVariable String postId) {
        return PostResponse.from(getFeedUseCase.getPost(postId));
    }

    @PostMapping("/{postId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(@PathVariable String postId, @Valid @RequestBody LikeRequest request) {
        likePostUseCase.like(postId, request.userId());
    }

    @GetMapping("/{postId}/comments")
    public List<CommentResponse> comments(@PathVariable String postId) {
        return getFeedUseCase.comments(postId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @PostMapping("/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse comment(@PathVariable String postId, @Valid @RequestBody CommentRequest request) {
        commentOnPostUseCase.comment(new CommentCommand(postId, request.authorId(), request.content()));
        return getFeedUseCase.comments(postId).stream()
                .reduce((first, second) -> second)
                .map(CommentResponse::from)
                .orElseThrow();
    }

    @PutMapping("/{postId}")
    public PostResponse edit(@PathVariable String postId, @Valid @RequestBody EditPostRequest request) {
        editPostUseCase.edit(
                new EditPostCommand(postId, request.requesterId(), request.content(), request.imageUrls()));
        return PostResponse.from(getFeedUseCase.getPost(postId));
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String postId, @RequestParam("requesterId") String requesterId) {
        deletePostUseCase.delete(postId, requesterId);
    }
}
