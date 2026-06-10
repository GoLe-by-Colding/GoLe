package com.gole.api.listing.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "listing_comments")
public class ListingCommentDocument {

    @Id
    private String id;

    @Indexed
    private String listingId;

    private String authorId;
    private String content;
    private boolean deleted;
    private Instant createdAt;

    protected ListingCommentDocument() {}

    public ListingCommentDocument(
            String id, String listingId, String authorId, String content, boolean deleted, Instant createdAt) {
        this.id = id;
        this.listingId = listingId;
        this.authorId = authorId;
        this.content = content;
        this.deleted = deleted;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getListingId() {
        return listingId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getContent() {
        return content;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
