package com.gole.api.notification.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 단말 푸시 토큰 영속 모델. 도메인 {@code DeviceToken}과 분리, 매핑은 어댑터가 담당한다.
 *
 * <p>FCM 등록 토큰을 {@code @Id}로 쓴다. 토큰이 곧 단말이므로 별도 키를 두면 유니크 인덱스를
 * 하나 더 만들어야 하고, 재등록이 upsert가 아니라 중복 삽입이 된다.
 * ({@code @Id}에는 {@code @Indexed(unique=true)}를 붙이지 않는다 — Mongo가 이미 보장한다.)
 */
@Document(collection = "device_tokens")
public class DeviceTokenDocument {

    @Id
    private String token;

    /** 수신자 조회가 발송 경로의 유일한 질의다. */
    @Indexed
    private String accountId;

    private String platform;
    private Instant registeredAt;

    protected DeviceTokenDocument() {
        // MongoDB 매핑용
    }

    public DeviceTokenDocument(String token, String accountId, String platform, Instant registeredAt) {
        this.token = token;
        this.accountId = accountId;
        this.platform = platform;
        this.registeredAt = registeredAt;
    }

    public String getToken() {
        return token;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getPlatform() {
        return platform;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
