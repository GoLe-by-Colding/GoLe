package com.gole.api.parts.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 부품 요청 MongoDB 영속 모델. 도메인 {@code PartRequest}와 분리돼 있고 매핑은
 * {@link MongoPartRequestAdapter}가 맡는다. 상태는 enum 이름 문자열로 저장한다.
 *
 * <p>인덱스는 세 조회 경로에 하나씩이다 — 게시판(상태별 최신순), 세트 상세(세트·상태별 최신순),
 * 내 요청(작성자별 최신순, 열린 요청 수 세기도 같은 인덱스를 탄다).
 */
@Document(collection = MongoPartRequestAdapter.COLLECTION)
@CompoundIndex(name = "ix_status_createdAt", def = "{'status': 1, 'createdAt': -1}")
@CompoundIndex(name = "ix_setNumber_status_createdAt", def = "{'setNumber': 1, 'status': 1, 'createdAt': -1}")
@CompoundIndex(name = "ix_requesterId_createdAt", def = "{'requesterId': 1, 'createdAt': -1}")
public record PartRequestDocument(
        @Id String id,
        String requesterId,
        String setNumber,
        List<ItemDocument> items,
        String note,
        String status,
        Instant createdAt,
        Instant closedAt) {

    /** 부품 한 줄. */
    public record ItemDocument(String partNumber, String colorName, int quantity) {}
}
