package com.gole.api.pricing.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 체결 거래 MongoDB 영속 모델. 순수 도메인 모델({@code PriceTransaction})과 분리되어 있으며
 * 매핑은 {@link PriceTransactionPersistenceAdapter}가 담당한다.
 */
@Document(collection = "price_transactions")
public class PriceTransactionDocument {

    @Id
    private String id;

    @Indexed
    private String setNumber;

    private long price;

    private int quantity;

    @Indexed
    private Instant executedAt;

    protected PriceTransactionDocument() {
        // MongoDB 매핑용
    }

    public PriceTransactionDocument(
            String id, String setNumber, long price, int quantity, Instant executedAt) {
        this.id = id;
        this.setNumber = setNumber;
        this.price = price;
        this.quantity = quantity;
        this.executedAt = executedAt;
    }

    public String getId() {
        return id;
    }

    public String getSetNumber() {
        return setNumber;
    }

    public long getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
