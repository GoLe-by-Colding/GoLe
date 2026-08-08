package com.gole.api.admin.application.port.in;

import com.gole.api.admin.domain.model.AdminAction;
import java.util.List;

/**
 * Inbound port: 감사 로그 조회(ADMIN 전용). (admin-console 요구사항 8.3, 8.6)
 */
public interface ListAdminActionsUseCase {

    List<AdminAction> recent(int limit);
}
