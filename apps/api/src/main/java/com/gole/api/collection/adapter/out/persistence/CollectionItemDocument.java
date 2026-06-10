package com.gole.api.collection.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 컬렉션 항목 MongoDB 영속 모델. 순수 도메인 모델({@code CollectionItem})과 분리되어 있으며
 * 매핑은 {@link CollectionItemPersistenceAdapter}가 담당한다. 보유 상태는 문자열로 저장한다.
 */
@Document(collection = "collection_items")
public class CollectionItemDocument {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String setNumber;

    private String status;

    private Instant createdAt;

    protected CollectionItemDocument() {
        // MongoDB 매핑용
    }

    public CollectionItemDocument(String id, String userId, String setNumber, String status, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.setNumber = setNumber;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getSetNumber() {
        return setNumber;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
