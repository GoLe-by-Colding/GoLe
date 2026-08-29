package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 소셜 채팅방(DIRECT·GROUP·SUPPORT) 영속 모델.
 *
 * <p><b>왜 별도 컬렉션인가.</b> 기존 {@code chat_rooms} 에는
 * {@code {buyerId, sellerId, listingId}} unique 인덱스가 걸려 있고
 * {@code auto-index-creation: true} 라 실제로 생성된다. MongoDB 의 unique 인덱스는 없는 필드를
 * null 로 취급하므로, 세 필드가 모두 비는 소셜 방은 <b>저장소 전체에 단 하나만</b> 존재할 수 있다.
 * 두 번째 DIRECT 방부터 duplicate key 로 실패한다.
 *
 * <p>그 인덱스를 partial 로 바꾸려면 운영 DB 에서 인덱스를 드롭해야 하고, 그 전에 배포된 인스턴스가
 * 옛 인덱스를 다시 만들면 부팅이 깨진다. 즉 <b>무중단이 되지 않는다.</b> 새 컬렉션에 쓰면 그
 * 조율 없이 오늘 동작하고, 매물 방은 기존 경로 그대로 살아 있다. 통합은 인덱스 교체가 끝난 뒤의
 * 별도 과제로 남긴다(spec T3).
 */
@Document(collection = "social_chat_rooms")
@CompoundIndex(name = "member_activity_idx", def = "{'memberIds': 1, 'lastMessageAt': -1}")
public class SocialChatRoomDocument {

    @Id
    private String id;

    private String type;
    private List<String> memberIds;
    private String ownerId;
    private String title;

    /** DIRECT 멱등 키. DIRECT 가 아니면 null 이라 sparse 로 둔다. */
    @Indexed(unique = true, sparse = true)
    private String dedupeKey;

    private Instant createdAt;
    private Instant lastMessageAt;
    private Instant closedAt;

    @Version
    private long version;

    protected SocialChatRoomDocument() {
        // MongoDB 매핑용
    }

    public SocialChatRoomDocument(
            String id,
            String type,
            List<String> memberIds,
            String ownerId,
            String title,
            String dedupeKey,
            Instant createdAt,
            Instant lastMessageAt,
            Instant closedAt,
            long version) {
        this.id = id;
        this.type = type;
        this.memberIds = memberIds;
        this.ownerId = ownerId;
        this.title = title;
        this.dedupeKey = dedupeKey;
        this.createdAt = createdAt;
        this.lastMessageAt = lastMessageAt;
        this.closedAt = closedAt;
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public long getVersion() {
        return version;
    }
}
