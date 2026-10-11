package com.gole.api.chat.adapter.out.admin;

import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.chat.application.port.out.SupportAdminActionPort;
import com.gole.api.chat.domain.model.SupportAdminAction;
import com.gole.api.chat.domain.model.SupportOperator;
import org.springframework.stereotype.Component;

/** 관리자 컨텍스트 통합 어댑터. 문의 콘솔 조치를 admin 의 감사 기록 유스케이스로 남긴다. */
@Component
public class AdminSupportActionAuditAdapter implements SupportAdminActionPort {

    private final RecordAdminActionUseCase audit;

    public AdminSupportActionAuditAdapter(RecordAdminActionUseCase audit) {
        this.audit = audit;
    }

    @Override
    public void record(SupportOperator operator, SupportAdminAction action, String targetId, String detail) {
        audit.record(new RecordAdminActionCommand(
                operator.id(),
                operator.email(),
                AdminActionType.valueOf(action.name()),
                targetType(action),
                targetId,
                detail));
    }

    private static AdminTargetType targetType(SupportAdminAction action) {
        return action == SupportAdminAction.SUPPORT_NOTIFICATION_REQUEUE
                ? AdminTargetType.SUPPORT_NOTIFICATION
                : AdminTargetType.SUPPORT_TICKET;
    }
}
