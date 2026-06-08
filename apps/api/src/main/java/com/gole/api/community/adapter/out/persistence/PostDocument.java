package com.gole.api.community.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 게시글 MongoDB 영속 모델. 순수 도메인 모델({@code Post})과 분리되어 있으며
 * 매핑은 {@link PostPersistenceAdapter}가 담당한다.
 *
 * <p>{@code type}/{@code status} 열거형은 이름 문자열로 저장한다.
 */
@Document(collection = "posts")
public class PostDocument {

    @Id
    private String id;

    @Indexed
    private String authorId;

    private String content;

    private List<String> imageUrls;

    private String type;

    @Indexed
    private String status;

    private Set<String> likedBy;

    private Instant createdAt;

    protected PostDocument() {
        // MongoDB 매핑용
    }

    public PostDocument(
            String id,
            String authorId,
            String content,
            List<String> imageUrls,
            String type,
            String status,
            Set<String> likedBy,
            Instant createdAt) {
        this.id = id;
        this.authorId = authorId;
        this.content = content;
        this.imageUrls = imageUrls;
        this.type = type;
        this.status = status;
        this.likedBy = likedBy;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getContent() {
        return content;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public Set<String> getLikedBy() {
        return likedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
