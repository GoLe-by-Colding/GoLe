package com.gole.api.admin.application.port.in;

import com.gole.api.admin.domain.model.AdminAction;
import com.gole.api.admin.domain.model.AdminActionType;
import java.util.List;

/**
 * Inbound port: 감사 로그 조회(ADMIN 전용). (admin-console 요구사항 8.3, 8.6)
 *
 * <p>{@link #countByType(AdminActionType)}은 다른 컨텍스트(promotion 등)가 감사 로그 건수를
 * 집계할 때 쓰는 통로다 — {@code AdminAuditPort}는 out-port라 다른 컨텍스트가 직접 의존하면
 * 안 되고, 반드시 이 인바운드 유스케이스를 거친다(AGENTS.md 컨텍스트 간 연동).
 */
public interface ListAdminActionsUseCase {

    List<AdminAction> recent(int limit);

    long countByType(AdminActionType type);
}
