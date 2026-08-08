package com.gole.api.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.application.port.out.AdminAuditPort;
import com.gole.api.admin.domain.model.AdminAction;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * admin-console 요구사항 8 — 감사 로그 기록/조회와 "감사 실패가 운영을 막지 않는다"는 계약.
 */
class AdminAuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T09:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("8.1 조치 정보를 시각과 함께 append 한다")
    void recordsAction() {
        RecordingPort port = new RecordingPort();
        RecordingPublisher publisher = new RecordingPublisher();
        AdminAuditService service = new AdminAuditService(port, FIXED, publisher);

        service.record(new RecordAdminActionCommand(
                "admin-1",
                "admin@gole.io",
                AdminActionType.LISTING_TAKEDOWN,
                AdminTargetType.LISTING,
                "listing-9",
                "가품 의심"));

        assertThat(port.appended).hasSize(1);
        AdminAction saved = port.appended.get(0);
        assertThat(saved.getActorId()).isEqualTo("admin-1");
        assertThat(saved.getActorEmail()).isEqualTo("admin@gole.io");
        assertThat(saved.getType()).isEqualTo(AdminActionType.LISTING_TAKEDOWN);
        assertThat(saved.getTargetId()).isEqualTo("listing-9");
        assertThat(saved.getReason()).isEqualTo("가품 의심");
        assertThat(saved.getOccurredAt()).isEqualTo(NOW);
        assertThat(publisher.events).singleElement().satisfies(event -> {
            assertThat(event.category()).isEqualTo(OperationalEvent.Category.ADMIN);
            assertThat(event.fields())
                    .containsEntry("조치", AdminActionType.LISTING_TAKEDOWN.name())
                    .doesNotContainKey("사유")
                    .doesNotContainValue("admin@gole.io");
        });
    }

    @Test
    @DisplayName("공백 사유는 null로 정규화된다")
    void blankReasonBecomesNull() {
        RecordingPort port = new RecordingPort();
        AdminAuditService service = new AdminAuditService(port, FIXED, event -> {});

        service.record(new RecordAdminActionCommand(
                "admin-1", "admin@gole.io", AdminActionType.REPORT_RESOLVE, AdminTargetType.REPORT, "r-1", "   "));

        assertThat(port.appended.get(0).getReason()).isNull();
    }

    @Test
    @DisplayName("8.5 저장소가 실패해도 예외를 전파하지 않는다 — 이미 성공한 조치를 되돌리지 않기 위해")
    void swallowsStorageFailure() {
        RecordingPublisher publisher = new RecordingPublisher();
        AdminAuditService service = new AdminAuditService(new FailingPort(), FIXED, publisher);

        assertThatCode(() -> service.record(new RecordAdminActionCommand(
                        "admin-1",
                        "admin@gole.io",
                        AdminActionType.ACCOUNT_SUSPEND,
                        AdminTargetType.ACCOUNT,
                        "u-1",
                        "사유")))
                .doesNotThrowAnyException();
        assertThat(publisher.events).isEmpty();
    }

    @Test
    @DisplayName("8.3 조회 limit은 1~200으로 클램프된다")
    void clampsLimit() {
        RecordingPort port = new RecordingPort();
        AdminAuditService service = new AdminAuditService(port, FIXED, event -> {});

        service.recent(0);
        assertThat(port.lastLimit).isEqualTo(1);

        service.recent(9999);
        assertThat(port.lastLimit).isEqualTo(200);
    }

    private static final class RecordingPort implements AdminAuditPort {
        private final List<AdminAction> appended = new ArrayList<>();
        private int lastLimit;

        @Override
        public void append(AdminAction action) {
            appended.add(action);
        }

        @Override
        public List<AdminAction> findRecent(int limit) {
            lastLimit = limit;
            return List.copyOf(appended);
        }
    }

    private static final class FailingPort implements AdminAuditPort {
        @Override
        public void append(AdminAction action) {
            throw new IllegalStateException("mongo down");
        }

        @Override
        public List<AdminAction> findRecent(int limit) {
            return List.of();
        }
    }

    private static final class RecordingPublisher implements OperationalEventPublisher {
        private final List<OperationalEvent> events = new ArrayList<>();

        @Override
        public void publish(OperationalEvent event) {
            events.add(event);
        }
    }
}
