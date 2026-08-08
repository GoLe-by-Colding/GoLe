package com.gole.api.admin.application.port.in;

import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;

/**
 * Inbound port: 관리자 조치 감사 기록. (admin-console 요구사항 8.1, 8.2)
 *
 * <p>조치가 <b>성공한 뒤</b>에만 호출한다. 거부·실패는 기록하지 않는다.
 */
public interface RecordAdminActionUseCase {

    void record(RecordAdminActionCommand command);

    record RecordAdminActionCommand(
            String actorId,
            String actorEmail,
            AdminActionType type,
            AdminTargetType targetType,
            String targetId,
            String reason) {}
}
