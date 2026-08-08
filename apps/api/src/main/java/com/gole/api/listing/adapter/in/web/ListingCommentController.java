package com.gole.api.listing.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.listing.adapter.out.persistence.ListingCommentDocument;
import com.gole.api.listing.adapter.out.persistence.ListingCommentMongoRepository;
import com.gole.api.listing.adapter.out.persistence.ListingMongoRepository;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매물 문의 댓글(Q&A). 댓글 저장 후 매물 판매자에게 알림을 전송한다(본인 제외, best-effort).
 */
@Tag(name = "Listing Q@RestControllerA", description = "매물 문의 댓글 조회·작성")
@RestController
@RequestMapping("/api/v1/listings/{listingId}/comments")
public class ListingCommentController {

    private final ListingCommentMongoRepository commentRepository;
    private final ListingMongoRepository listingRepository;
    private final NotifyUseCase notifyUseCase;

    public ListingCommentController(
            ListingCommentMongoRepository commentRepository,
            ListingMongoRepository listingRepository,
            NotifyUseCase notifyUseCase) {
        this.commentRepository = commentRepository;
        this.listingRepository = listingRepository;
        this.notifyUseCase = notifyUseCase;
    }

    @GetMapping
    public List<CommentResponse> list(@PathVariable String listingId) {
        return commentRepository.findByListingIdAndDeletedFalseOrderByCreatedAtAsc(listingId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(
            @PathVariable String listingId, @Valid @RequestBody CreateCommentRequest req, HttpServletRequest http) {
        String authorId = AuthenticatedUser.id(http);
        ListingCommentDocument doc = new ListingCommentDocument(
                UUID.randomUUID().toString(), listingId, authorId, req.content(), false, Instant.now());
        CommentResponse saved = CommentResponse.from(commentRepository.save(doc));

        // 판매자에게 Q&A 알림(본인 댓글 제외, best-effort).
        listingRepository.findById(listingId).ifPresent(listing -> {
            if (!listing.getSellerId().equals(authorId)) {
                try {
                    notifyUseCase.notify(new NotifyCommand(
                            listing.getSellerId(),
                            NotificationType.COMMENT,
                            "매물 '" + truncate(listing.getTitle(), 20) + "'에 문의가 달렸어요.",
                            "/listings/" + listingId));
                } catch (RuntimeException ignored) {
                    // 알림 실패는 댓글 저장을 막지 않는다.
                }
            }
        });

        return saved;
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }

    public record CreateCommentRequest(
            String authorId, @NotBlank @jakarta.validation.constraints.Size(max = 1000) String content) {}

    public record CommentResponse(String id, String authorId, String content, Instant createdAt) {

        public static CommentResponse from(ListingCommentDocument d) {
            return new CommentResponse(d.getId(), d.getAuthorId(), d.getContent(), d.getCreatedAt());
        }
    }
}
