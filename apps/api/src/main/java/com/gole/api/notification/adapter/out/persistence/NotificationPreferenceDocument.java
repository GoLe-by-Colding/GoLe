package com.gole.api.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 알림 수신 설정 영속 모델. 도메인 {@code NotificationPreferences}와 분리, 매핑은 어댑터가 담당한다.
 *
 * <p>계정당 한 문서라 계정 id를 {@code _id}로 쓴다. 꺼둔 분류만 이름으로 담고, 문서가 없으면 전부 켜짐이다.
 */
@Document(collection = NotificationPreferenceDocument.COLLECTION)
public class NotificationPreferenceDocument {

    static final String COLLECTION = "notification_preferences";

    @Id
    private String accountId;

    private List<String> disabledCategories;
    private Instant updatedAt;

    protected NotificationPreferenceDocument() {
        // MongoDB 매핑용
    }

    public NotificationPreferenceDocument(String accountId, List<String> disabledCategories, Instant updatedAt) {
        this.accountId = accountId;
        this.disabledCategories = disabledCategories;
        this.updatedAt = updatedAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public List<String> getDisabledCategories() {
        return disabledCategories;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
