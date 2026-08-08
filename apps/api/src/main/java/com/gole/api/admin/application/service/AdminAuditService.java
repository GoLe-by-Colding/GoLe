package com.gole.api.admin.application.service;

import com.gole.api.admin.application.port.in.ListAdminActionsUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.out.AdminAuditPort;
import com.gole.api.admin.domain.model.AdminAction;
import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 감사 로그 유스케이스. (admin-console 요구사항 8)
 *
 * <p>핵심 설계 결정: <b>감사 실패가 운영을 막지 않는다</b>(요구사항 8.5).
 * 조치는 이미 성공한 뒤에 기록되므로, 여기서 예외를 던지면 "매물은 내려갔는데 API는 500"이라는
 * 최악의 상태가 된다. 따라서 저장 실패는 에러 로그만 남기고 삼킨다.
 */
@Service
public class AdminAuditService implements RecordAdminActionUseCase, ListAdminActionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);
    private static final int MAX_LIMIT = 200;

    private final AdminAuditPort auditPort;
    private final Clock clock;
    private final OperationalEventPublisher operationalEventPublisher;

    public AdminAuditService(
            AdminAuditPort auditPort, Clock clock, OperationalEventPublisher operationalEventPublisher) {
        this.auditPort = auditPort;
        this.clock = clock;
        this.operationalEventPublisher = operationalEventPublisher;
    }

    @Override
    public void record(RecordAdminActionCommand command) {
        AdminAction action = new AdminAction(
                UUID.randomUUID().toString(),
                command.actorId(),
                command.actorEmail(),
                command.type(),
                command.targetType(),
                command.targetId(),
                normalize(command.reason()),
                Instant.now(clock));
        try {
            auditPort.append(action);
        } catch (RuntimeException ex) {
            // 요구사항 8.5: 감사 기록 실패로 이미 성공한 조치를 되돌리지 않는다.
            log.error(
                    "감사 로그 기록 실패 — actor={}, type={}, target={}:{}",
                    command.actorId(),
                    command.type(),
                    command.targetType(),
                    command.targetId(),
                    ex);
            return;
        }

        try {
            // 운영 채널에는 이메일·사유를 제외한 최소 식별자만 전달한다.
            operationalEventPublisher.publish(new OperationalEvent(
                    Category.ADMIN,
                    levelFor(command.type()),
                    "관리자 조치 완료",
                    "관리자 콘솔에서 운영 조치가 실행되었습니다.",
                    Map.of(
                            "조치", command.type().name(),
                            "대상", command.targetType().name(),
                            "대상 ID", command.targetId(),
                            "관리자 ID", command.actorId()),
                    action.getOccurredAt()));
        } catch (RuntimeException ex) {
            // 알림 실패는 감사 로그 성공과 분리한다. 운영 조치를 되돌리거나 감사 실패로 오인하지 않는다.
            log.warn(
                    "관리자 운영 알림 발행 실패 — actor={}, type={}, target={}:{}",
                    command.actorId(),
                    command.type(),
                    command.targetType(),
                    command.targetId(),
                    ex);
        }
    }

    @Override
    public List<AdminAction> recent(int limit) {
        return auditPort.findRecent(Math.max(1, Math.min(limit, MAX_LIMIT)));
    }

    private static String normalize(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private static Level levelFor(com.gole.api.admin.domain.model.AdminActionType type) {
        return switch (type) {
            case LISTING_TAKEDOWN, POST_REMOVE, ACCOUNT_SUSPEND -> Level.WARNING;
            default -> Level.INFO;
        };
    }
}
