package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "chat_rooms")
@CompoundIndexes({
    @CompoundIndex(def = "{'buyerId': 1, 'sellerId': 1, 'listingId': 1}", unique = true),
    @CompoundIndex(name = "chat_room_buyer_activity_idx", def = "{'buyerId': 1, 'lastMessageAt': -1}"),
    @CompoundIndex(name = "chat_room_seller_activity_idx", def = "{'sellerId': 1, 'lastMessageAt': -1}")
})
public class ChatRoomDocument {

    @Id
    private String id;

    private String listingId;
    private String buyerId;
    private String sellerId;
    private Instant createdAt;
    private Instant lastMessageAt;
    private Instant buyerConfirmedAt;
    private Instant sellerConfirmedAt;
    private Instant directTradeCompletedAt;

    protected ChatRoomDocument() {}

    public ChatRoomDocument(String id, String listingId, String buyerId, String sellerId, Instant createdAt) {
        this.id = id;
        this.listingId = listingId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.createdAt = createdAt;
        this.lastMessageAt = createdAt;
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

    public Instant getBuyerConfirmedAt() {
        return buyerConfirmedAt;
    }

    public Instant getSellerConfirmedAt() {
        return sellerConfirmedAt;
    }

    public Instant getDirectTradeCompletedAt() {
        return directTradeCompletedAt;
    }
}
