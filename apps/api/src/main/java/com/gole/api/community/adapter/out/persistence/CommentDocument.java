package com.gole.api.community.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 댓글 MongoDB 영속 모델. 순수 도메인 모델({@code Comment})과 분리되어 있으며
 * 매핑은 {@link CommentPersistenceAdapter}가 담당한다.
 */
@Document(collection = "comments")
public class CommentDocument {

    @Id
    private String id;

    @Indexed
    private String postId;

    private String authorId;

    private String content;

    private Instant createdAt;

    protected CommentDocument() {
        // MongoDB 매핑용
    }

    public CommentDocument(String id, String postId, String authorId, String content, Instant createdAt) {
        this.id = id;
        this.postId = postId;
        this.authorId = authorId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getPostId() {
        return postId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
