package com.gole.api.listing.adapter.in.web;

import com.gole.api.listing.adapter.out.persistence.ListingCommentDocument;
import com.gole.api.listing.adapter.out.persistence.ListingCommentMongoRepository;
import com.gole.api.listing.domain.model.ListingComment;
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
 * 매물 문의 댓글(Q&A). 기존 ListingController 와 분리해 단일 책임을 유지한다.
 * 직접 리포지토리를 사용한다 — 조회/저장만이라 서비스 계층 불필요.
 */
@RestController
@RequestMapping("/api/v1/listings/{listingId}/comments")
public class ListingCommentController {

    private final ListingCommentMongoRepository repository;

    public ListingCommentController(ListingCommentMongoRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<CommentResponse> list(@PathVariable String listingId) {
        return repository
                .findByListingIdAndDeletedFalseOrderByCreatedAtAsc(listingId)
                .stream()
                .map(CommentResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(@PathVariable String listingId, @Valid @RequestBody CreateCommentRequest req) {
        ListingCommentDocument doc = new ListingCommentDocument(
                UUID.randomUUID().toString(), listingId, req.authorId(), req.content(), false, Instant.now());
        return CommentResponse.from(repository.save(doc));
    }

    public record CreateCommentRequest(@NotBlank String authorId, @NotBlank String content) {}

    public record CommentResponse(
            String id, String authorId, String content, Instant createdAt) {

        public static CommentResponse from(ListingCommentDocument d) {
            return new CommentResponse(d.getId(), d.getAuthorId(), d.getContent(), d.getCreatedAt());
        }
    }
}
