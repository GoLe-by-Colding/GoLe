package com.gole.api.discovery.adapter.out.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 팔로우 관계 MongoDB 영속 모델. 순수 도메인 모델({@code Follow})과 분리되어 있으며
 * 매핑은 {@link FollowPersistenceAdapter}가 담당한다.
 */
@Document(collection = "follows")
@CompoundIndex(name = "uq_follower_seller", def = "{'userId': 1, 'sellerId': 1}", unique = true)
public class FollowDocument {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String sellerId;

    protected FollowDocument() {
        // MongoDB 매핑용
    }

    public FollowDocument(String id, String userId, String sellerId) {
        this.id = id;
        this.userId = userId;
        this.sellerId = sellerId;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getSellerId() {
        return sellerId;
    }
}
