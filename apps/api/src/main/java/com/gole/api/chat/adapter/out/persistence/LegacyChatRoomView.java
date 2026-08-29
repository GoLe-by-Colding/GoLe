package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 기존 {@code chat_rooms} 문서를 <b>읽기 전용</b>으로 보는 매핑.
 *
 * <p>쓰기는 하지 않는다 — 매물 방의 생성·직거래 확인은 기존 경로가 계속 소유한다. 여기서는
 * 소셜 도메인이 매물 방도 같은 규칙(멤버십·유형)으로 다룰 수 있도록 읽기만 한다.
 *
 * <p>인덱스를 하나도 선언하지 않은 것은 의도다. 같은 컬렉션에 매핑된 클래스가 인덱스를 선언하면
 * 기존 인덱스와 옵션이 어긋나 부팅이 깨질 수 있다.
 */
@Document(collection = "chat_rooms")
public class LegacyChatRoomView {

    @Id
    private String id;

    private String listingId;
    private String buyerId;
    private String sellerId;
    private Instant createdAt;
    private Instant lastMessageAt;

    protected LegacyChatRoomView() {
        // MongoDB 매핑용
    }

    public String getId() {
        return id;
    }

    public String getListingId() {
        return listingId;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt == null ? createdAt : lastMessageAt;
    }

    /** 레거시 문서의 멤버는 언제나 구매자와 판매자 둘이다. */
    public List<String> memberIds() {
        return List.of(buyerId, sellerId);
    }
}
