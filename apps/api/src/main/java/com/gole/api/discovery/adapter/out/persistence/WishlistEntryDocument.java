package com.gole.api.discovery.adapter.out.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 위시리스트 항목 MongoDB 영속 모델. 순수 도메인 모델({@code WishlistEntry})과 분리되어 있으며
 * 매핑은 {@link WishlistPersistenceAdapter}가 담당한다.
 *
 * <p>{@code targetType}은 enum 이름(문자열)으로 저장한다.
 */
@Document(collection = "wishlist_entries")
@CompoundIndex(name = "uq_user_target", def = "{'userId': 1, 'targetType': 1, 'targetId': 1}", unique = true)
public class WishlistEntryDocument {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String targetType;

    private String targetId;

    protected WishlistEntryDocument() {
        // MongoDB 매핑용
    }

    public WishlistEntryDocument(String id, String userId, String targetType, String targetId) {
        this.id = id;
        this.userId = userId;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }
}
