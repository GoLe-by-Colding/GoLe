package com.gole.api.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.BadRequestException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationPreferencesTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");

    @Test
    @DisplayName("기본값은 모든 분류가 켜져 있다")
    void defaults_enableEveryCategory() {
        NotificationPreferences defaults = NotificationPreferences.defaults("u1");

        for (NotificationCategory category : NotificationCategory.values()) {
            assertThat(defaults.isEnabled(category)).as(category.key()).isTrue();
        }
        for (NotificationType type : NotificationType.values()) {
            assertThat(defaults.allows(type)).as(type.name()).isTrue();
        }
        assertThat(defaults.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("끈 분류의 알림 종류만 막는다")
    void allows_blocksOnlyTypesOfDisabledCategory() {
        NotificationPreferences preferences =
                NotificationPreferences.defaults("u1").update(Map.of(NotificationCategory.OFFER, false), NOW);

        assertThat(preferences.allows(NotificationType.OFFER_RECEIVED)).isFalse();
        assertThat(preferences.allows(NotificationType.BID_FILLED)).isFalse();
        assertThat(preferences.allows(NotificationType.COMMENT)).isTrue();
        assertThat(preferences.allows(NotificationType.ORDER_PAID)).isTrue();
        assertThat(preferences.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("빠진 분류는 그대로 두고 받은 분류만 바꾼다")
    void update_leavesMissingCategoriesUntouched() {
        NotificationPreferences start = new NotificationPreferences(
                "u1", List.of(NotificationCategory.WATCH, NotificationCategory.COMMUNITY), NOW);

        NotificationPreferences next = start.update(
                Map.of(NotificationCategory.WATCH, true, NotificationCategory.OFFER, false), NOW.plusSeconds(1));

        assertThat(next.getDisabled())
                .containsExactlyInAnyOrder(NotificationCategory.OFFER, NotificationCategory.COMMUNITY);
        // 원본은 바뀌지 않는다.
        assertThat(start.getDisabled())
                .containsExactlyInAnyOrder(NotificationCategory.WATCH, NotificationCategory.COMMUNITY);
    }

    @Test
    @DisplayName("거래 진행을 끄려 하면 거부한다")
    void update_rejectsTurningOffMandatoryCategory() {
        NotificationPreferences preferences = NotificationPreferences.defaults("u1");

        assertThatThrownBy(() -> preferences.update(
                        Map.of(NotificationCategory.TRADE, false, NotificationCategory.OFFER, false), NOW))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode())
                .isEqualTo("NOTIFICATION_CATEGORY_MANDATORY");
    }

    @Test
    @DisplayName("거래 진행을 켜는 요청은 받아들인다")
    void update_acceptsTurningOnMandatoryCategory() {
        NotificationPreferences next = NotificationPreferences.defaults("u1")
                .update(Map.of(NotificationCategory.TRADE, true, NotificationCategory.WATCH, false), NOW);

        assertThat(next.getDisabled()).containsExactly(NotificationCategory.WATCH);
        assertThat(next.isEnabled(NotificationCategory.TRADE)).isTrue();
    }

    @Test
    @DisplayName("저장값에 거래 진행이 꺼져 있어도 무시한다")
    void constructor_dropsMandatoryCategoryFromStoredValue() {
        NotificationPreferences preferences =
                new NotificationPreferences("u1", List.of(NotificationCategory.TRADE, NotificationCategory.OFFER), NOW);

        assertThat(preferences.getDisabled()).containsExactly(NotificationCategory.OFFER);
        assertThat(preferences.allows(NotificationType.GENERAL)).isTrue();
    }
}
