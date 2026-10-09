package com.gole.api.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.application.port.out.NotificationIdGeneratorPort;
import com.gole.api.notification.application.port.out.NotificationRepositoryPort;
import com.gole.api.notification.domain.model.DevicePlatform;
import com.gole.api.notification.domain.model.DeviceToken;
import com.gole.api.notification.domain.model.Notification;
import com.gole.api.notification.domain.model.NotificationCategory;
import com.gole.api.notification.domain.model.NotificationType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 가짜 포트로 알림 유스케이스를 검증한다. (알림 스펙 N1~N5)
 */
class NotificationServiceTest {

    private InMemoryRepo repo;
    private RecordingPushSender pushSender;
    private InMemoryDeviceTokens deviceTokens;
    private InMemoryNotificationPreferences preferences;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryRepo();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        pushSender = new RecordingPushSender();
        deviceTokens = new InMemoryDeviceTokens();
        // 실행기를 동기로 준다. 발송이 다른 스레드에서 일어나면 단정이 경합에 흔들린다.
        PushDispatcher dispatcher = new PushDispatcher(deviceTokens, pushSender, Runnable::run);
        preferences = new InMemoryNotificationPreferences();
        service = new NotificationService(
                repo, new SequentialIds(), dispatcher, new NotificationPreferenceGate(preferences), clock);
    }

    @Test
    void notify_createsUnread() {
        String id = service.notify(new NotifyCommand("u1", NotificationType.ORDER_PLACED, "주문!", "/orders/o1"));

        assertThat(id).isEqualTo("noti-1");
        assertThat(service.unreadCount("u1")).isEqualTo(1);
        assertThat(service.list("u1")).singleElement().satisfies(n -> {
            assertThat(n.getMessage()).isEqualTo("주문!");
            assertThat(n.isRead()).isFalse();
        });
    }

    @Test
    void notify_withSameDeduplicationKeyCreatesOnlyOneNotification() {
        NotifyCommand command =
                new NotifyCommand("u1", NotificationType.POST_LIKED, "누군가 내 글을 좋아해요", "/community/p1", "like:p1:u2");

        String firstId = service.notify(command);
        String repeatedId = service.notify(command);

        assertThat(repeatedId).isEqualTo(firstId);
        assertThat(service.list("u1")).hasSize(1);
        assertThat(service.unreadCount("u1")).isEqualTo(1);
    }

    @Test
    void markRead_byOwner_marksRead() {
        String id = service.notify(new NotifyCommand("u1", NotificationType.GENERAL, "안녕", null));
        service.markRead(id, "u1");
        assertThat(service.unreadCount("u1")).isZero();
    }

    @Test
    void markRead_byNonOwner_isIgnored() {
        String id = service.notify(new NotifyCommand("u1", NotificationType.GENERAL, "안녕", null));
        service.markRead(id, "intruder");
        assertThat(service.unreadCount("u1")).isEqualTo(1);
    }

    @Test
    void markAllRead_clearsUnread() {
        service.notify(new NotifyCommand("u1", NotificationType.GENERAL, "1", null));
        service.notify(new NotifyCommand("u1", NotificationType.GENERAL, "2", null));
        service.notify(new NotifyCommand("u2", NotificationType.GENERAL, "other", null));

        service.markAllRead("u1");

        assertThat(service.unreadCount("u1")).isZero();
        assertThat(service.unreadCount("u2")).isEqualTo(1); // 다른 사용자는 영향 없음
    }

    @Test
    void notify_suppressedCategoryIsNeitherStoredNorPushed() {
        deviceTokens.upsert(new DeviceToken("token-1", "u1", DevicePlatform.ANDROID, Instant.EPOCH));
        preferences.disable("u1", NotificationCategory.COMMUNITY);

        String id = service.notify(new NotifyCommand("u1", NotificationType.COMMENT, "새 댓글", "/community/p1"));

        assertThat(id).isNull();
        assertThat(service.list("u1")).isEmpty();
        assertThat(pushSender.sent).isEmpty();
    }

    @Test
    void notify_otherCategoriesStillDeliveredWhenOneIsSuppressed() {
        deviceTokens.upsert(new DeviceToken("token-1", "u1", DevicePlatform.ANDROID, Instant.EPOCH));
        preferences.disable("u1", NotificationCategory.COMMUNITY);

        String id = service.notify(new NotifyCommand("u1", NotificationType.OFFER_RECEIVED, "가격 제안", "/offers"));

        assertThat(id).isNotNull();
        assertThat(service.list("u1")).hasSize(1);
        assertThat(pushSender.sent).hasSize(1);
    }

    @Test
    void notify_suppressionAppliesOnlyToTheRecipientWhoOptedOut() {
        preferences.disable("u1", NotificationCategory.WATCH);

        service.notify(new NotifyCommand("u1", NotificationType.WATCHED_SET_LISTING, "새 매물", "/listings/l1"));
        service.notify(new NotifyCommand("u2", NotificationType.WATCHED_SET_LISTING, "새 매물", "/listings/l1"));

        assertThat(service.list("u1")).isEmpty();
        assertThat(service.list("u2")).hasSize(1);
    }

    @Test
    void notify_mandatoryTradeCategoryIsAlwaysDeliveredWithoutLookup() {
        deviceTokens.upsert(new DeviceToken("token-1", "u1", DevicePlatform.IOS, Instant.EPOCH));
        // 저장소에 어떻게든 TRADE가 꺼진 값이 들어와 있어도 도메인이 무시한다.
        preferences.disable("u1", NotificationCategory.TRADE, NotificationCategory.OFFER);

        String id = service.notify(new NotifyCommand("u1", NotificationType.ORDER_PAID, "결제 완료", "/orders/o1"));

        assertThat(id).isNotNull();
        assertThat(service.list("u1")).hasSize(1);
        assertThat(pushSender.sent).hasSize(1);
        assertThat(preferences.findCalls).isZero();
    }

    @Test
    void notify_preferenceLookupFailureFailsOpen() {
        deviceTokens.upsert(new DeviceToken("token-1", "u1", DevicePlatform.ANDROID, Instant.EPOCH));
        preferences.disable("u1", NotificationCategory.COMMUNITY);
        preferences.failWith(new IllegalStateException("mongo down"));

        String id = service.notify(new NotifyCommand("u1", NotificationType.COMMENT, "새 댓글", "/community/p1"));

        assertThat(id).isNotNull();
        assertThat(service.list("u1")).hasSize(1);
        assertThat(pushSender.sent).hasSize(1);
    }

    private static final class InMemoryRepo implements NotificationRepositoryPort {
        private final List<Notification> store = new ArrayList<>();

        @Override
        public Notification save(Notification notification) {
            store.removeIf(n -> n.getId().equals(notification.getId()));
            store.add(notification);
            return notification;
        }

        @Override
        public Notification saveOnce(Notification notification) {
            if (notification.getDeduplicationKey() == null) {
                return save(notification);
            }
            return store.stream()
                    .filter(existing -> existing.getRecipientId().equals(notification.getRecipientId()))
                    .filter(existing -> notification.getDeduplicationKey().equals(existing.getDeduplicationKey()))
                    .findFirst()
                    .orElseGet(() -> save(notification));
        }

        @Override
        public List<Notification> findByRecipientNewestFirst(String recipientId) {
            return store.stream()
                    .filter(n -> n.getRecipientId().equals(recipientId))
                    .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                    .toList();
        }

        @Override
        public long countUnread(String recipientId) {
            return store.stream()
                    .filter(n -> n.getRecipientId().equals(recipientId) && !n.isRead())
                    .count();
        }

        @Override
        public Optional<Notification> findById(String id) {
            return store.stream().filter(n -> n.getId().equals(id)).findFirst();
        }

        @Override
        public void markAllRead(String recipientId) {
            store.stream().filter(n -> n.getRecipientId().equals(recipientId)).forEach(Notification::markRead);
        }
    }

    private static final class SequentialIds implements NotificationIdGeneratorPort {
        private int n = 0;

        @Override
        public String newNotificationId() {
            return "noti-" + (++n);
        }
    }
}
