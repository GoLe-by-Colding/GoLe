package com.gole.api.listing.adapter.in.web;

import com.gole.api.common.web.auth.AuthenticatedUser;
import com.gole.api.listing.application.port.in.ListListingCommentsUseCase;
import com.gole.api.listing.application.port.in.PostListingCommentUseCase;
import com.gole.api.listing.application.port.in.PostListingCommentUseCase.PostListingCommentCommand;
import com.gole.api.listing.domain.model.ListingComment;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 매물 문의 댓글(Q&A) 조회·작성. 규칙(공개 매물만·판매자 신원확인·판매자 알림)은 {@code ListingCommentService} 에 있다. */
@Tag(name = "Listing Q&A", description = "매물 문의 댓글 조회·작성")
@RestController
@RequestMapping("/api/v1/listings/{listingId}/comments")
public class ListingCommentController {

    private final ListListingCommentsUseCase listComments;
    private final PostListingCommentUseCase postComment;

    public ListingCommentController(ListListingCommentsUseCase listComments, PostListingCommentUseCase postComment) {
        this.listComments = listComments;
        this.postComment = postComment;
    }

    @GetMapping
    public List<CommentResponse> list(@PathVariable String listingId) {
        return listComments.list(listingId).stream().map(CommentResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(
            @PathVariable String listingId, @Valid @RequestBody CreateCommentRequest req, HttpServletRequest http) {
        // 작성자는 세션이 정한다 — 요청 본문의 authorId 는 쓰지 않는다(구 클라이언트 호환으로 필드만 남김).
        return CommentResponse.from(
                postComment.post(new PostListingCommentCommand(listingId, AuthenticatedUser.id(http), req.content())));
    }

    public record CreateCommentRequest(
            String authorId,

            @NotBlank @Size(max = ListingComment.MAX_CONTENT_LENGTH)
            String content) {}

    public record CommentResponse(String id, String authorId, String content, Instant createdAt) {

        public static CommentResponse from(ListingComment comment) {
            return new CommentResponse(comment.id(), comment.authorId(), comment.content(), comment.createdAt());
        }
    }
}
