package com.gole.api.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.notification.domain.model.NotificationCategory;
import com.gole.api.notification.domain.model.NotificationPreferences;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationPreferenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");

    private InMemoryNotificationPreferences repository;
    private NotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryNotificationPreferences();
        service = new NotificationPreferenceService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void get_returnsDefaultsWithoutSavingWhenNothingStored() {
        NotificationPreferences preferences = service.get("u1");

        assertThat(preferences.getAccountId()).isEqualTo("u1");
        assertThat(preferences.getDisabled()).isEmpty();
        assertThat(repository.rows).isEmpty();
    }

    @Test
    void update_persistsChangesAndKeepsMissingKeys() {
        service.update("u1", Map.of(NotificationCategory.WATCH, false, NotificationCategory.COMMUNITY, false));

        NotificationPreferences next = service.update("u1", Map.of(NotificationCategory.WATCH, true));

        assertThat(next.getDisabled()).containsExactly(NotificationCategory.COMMUNITY);
        assertThat(next.getUpdatedAt()).isEqualTo(NOW);
        assertThat(service.get("u1").getDisabled()).containsExactly(NotificationCategory.COMMUNITY);
    }

    @Test
    void update_rejectsMandatoryWithoutSaving() {
        assertThatThrownBy(() -> service.update("u1", Map.of(NotificationCategory.TRADE, false)))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", "NOTIFICATION_CATEGORY_MANDATORY");

        assertThat(repository.rows).isEmpty();
    }

    @Test
    void gate_optedOutReturnsOnlyAccountsThatDisabledTheCategory() {
        repository.disable("a", NotificationCategory.WATCH).disable("b", NotificationCategory.OFFER);
        NotificationPreferenceGate gate = new NotificationPreferenceGate(repository);

        assertThat(gate.optedOut(NotificationCategory.WATCH, java.util.List.of("a", "b", "c")))
                .containsExactly("a");
        assertThat(gate.optedOut(NotificationCategory.TRADE, java.util.List.of("a", "b")))
                .isEmpty();
        assertThat(gate.allows("a", NotificationCategory.WATCH)).isFalse();
        assertThat(gate.allows("c", NotificationCategory.WATCH)).isTrue();
    }
}
