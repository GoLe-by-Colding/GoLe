package com.gole.api.collection.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 컬렉션 가치 스냅샷 영속 모델. 도메인 {@code CollectionValueSnapshot}과 분리, 매핑은 어댑터가 담당한다.
 *
 * <p>{@code date}는 Asia/Seoul 기준 {@code yyyy-MM-dd} 문자열이다. 사전순이 곧 날짜순이라 범위 조회가 문자열
 * 비교로 끝나고, 시간대가 섞인 {@code Date}를 다시 날짜로 자를 필요가 없다. 사용자·날짜 유일 인덱스가 하루 한
 * 점을 보장한다(H1). {@code capturedAt} TTL로 400일 지나면 지운다(H6).
 */
@Document(collection = CollectionValueSnapshotDocument.COLLECTION)
@CompoundIndex(name = "ux_user_date", def = "{'userId': 1, 'date': 1}", unique = true)
public class CollectionValueSnapshotDocument {

    static final String COLLECTION = "collection_value_snapshots";

    @Id
    private String id;

    private String userId;
    private String date;
    private long ownedValue;
    private int ownedCount;
    private int pricedCount;

    @Indexed(name = "ttl_captured_at", expireAfter = "400d")
    private Instant capturedAt;

    protected CollectionValueSnapshotDocument() {
        // MongoDB 매핑용
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getDate() {
        return date;
    }

    public long getOwnedValue() {
        return ownedValue;
    }

    public int getOwnedCount() {
        return ownedCount;
    }

    public int getPricedCount() {
        return pricedCount;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }
}
