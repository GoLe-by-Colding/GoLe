package com.gole.api.listing.adapter.out.persistence;

import com.gole.api.listing.application.port.out.ListingCommentRepositoryPort;
import com.gole.api.listing.domain.model.ListingComment;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** 매물 문의 댓글 영속성 어댑터. 도메인 {@link ListingComment}와 {@link ListingCommentDocument}를 매핑한다. */
@Component
public class ListingCommentPersistenceAdapter implements ListingCommentRepositoryPort {

    private final ListingCommentMongoRepository repository;

    public ListingCommentPersistenceAdapter(ListingCommentMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public ListingComment save(ListingComment comment) {
        return toDomain(repository.save(new ListingCommentDocument(
                comment.id(),
                comment.listingId(),
                comment.authorId(),
                comment.content(),
                comment.deleted(),
                comment.createdAt())));
    }

    @Override
    public List<ListingComment> findActiveByListingId(String listingId, int limit) {
        return repository
                .findByListingIdAndDeletedFalse(
                        listingId, PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "createdAt")))
                .stream()
                .map(ListingCommentPersistenceAdapter::toDomain)
                .toList();
    }

    private static ListingComment toDomain(ListingCommentDocument document) {
        return new ListingComment(
                document.getId(),
                document.getListingId(),
                document.getAuthorId(),
                document.getContent(),
                document.isDeleted(),
                document.getCreatedAt());
    }
}
