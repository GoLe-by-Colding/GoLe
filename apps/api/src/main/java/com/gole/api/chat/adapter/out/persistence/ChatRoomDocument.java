package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "chat_rooms")
@CompoundIndex(def = "{'buyerId': 1, 'sellerId': 1, 'listingId': 1}", unique = true)
public class ChatRoomDocument {

    @Id
    private String id;

    private String listingId;
    private String buyerId;
    private String sellerId;
    private Instant createdAt;

    protected ChatRoomDocument() {}

    public ChatRoomDocument(String id, String listingId, String buyerId, String sellerId, Instant createdAt) {
        this.id = id;
        this.listingId = listingId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getListingId() { return listingId; }
    public String getBuyerId() { return buyerId; }
    public String getSellerId() { return sellerId; }
    public Instant getCreatedAt() { return createdAt; }
}
