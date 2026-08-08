package com.gole.api.admin.application.service;

import com.gole.api.admin.application.port.in.ListAdminActionsUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.out.AdminAuditPort;
import com.gole.api.admin.domain.model.AdminAction;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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

    public AdminAuditService(AdminAuditPort auditPort, Clock clock) {
        this.auditPort = auditPort;
        this.clock = clock;
    }

    @Override
    public void record(RecordAdminActionCommand command) {
        try {
            auditPort.append(new AdminAction(
                    UUID.randomUUID().toString(),
                    command.actorId(),
                    command.actorEmail(),
                    command.type(),
                    command.targetType(),
                    command.targetId(),
                    normalize(command.reason()),
                    Instant.now(clock)));
        } catch (RuntimeException ex) {
            // 요구사항 8.5: 감사 기록 실패로 이미 성공한 조치를 되돌리지 않는다.
            log.error(
                    "감사 로그 기록 실패 — actor={}, type={}, target={}:{}",
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
}
