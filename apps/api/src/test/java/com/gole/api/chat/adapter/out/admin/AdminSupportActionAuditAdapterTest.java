package com.gole.api.chat.adapter.out.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.chat.domain.model.SupportAdminAction;
import com.gole.api.chat.domain.model.SupportOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class AdminSupportActionAuditAdapterTest {

    private final RecordAdminActionUseCase audit = mock(RecordAdminActionUseCase.class);
    private final AdminSupportActionAuditAdapter adapter = new AdminSupportActionAuditAdapter(audit);
    private final SupportOperator operator = new SupportOperator("admin-1", "admin@gole.test");

    @ParameterizedTest
    @EnumSource(SupportAdminAction.class)
    @DisplayName("문의 콘솔 조치는 모두 같은 이름의 관리자 감사 유형으로 남는다")
    void record_mapsEveryActionToSameNamedAdminAction(SupportAdminAction action) {
        adapter.record(operator, action, "target-1", "detail");

        ArgumentCaptor<RecordAdminActionCommand> command = ArgumentCaptor.forClass(RecordAdminActionCommand.class);
        verify(audit).record(command.capture());
        assertThat(command.getValue().type()).isEqualTo(AdminActionType.valueOf(action.name()));
        assertThat(command.getValue().actorId()).isEqualTo("admin-1");
        assertThat(command.getValue().actorEmail()).isEqualTo("admin@gole.test");
        assertThat(command.getValue().targetId()).isEqualTo("target-1");
        assertThat(command.getValue().reason()).isEqualTo("detail");
    }

    @Test
    @DisplayName("알림 재큐잉은 알림 이벤트를, 나머지는 문의 티켓을 대상으로 남긴다")
    void record_choosesTargetTypeByAction() {
        adapter.record(operator, SupportAdminAction.SUPPORT_NOTIFICATION_REQUEUE, "event-1", null);
        adapter.record(operator, SupportAdminAction.SUPPORT_RESOLVE, "room-1", null);

        ArgumentCaptor<RecordAdminActionCommand> command = ArgumentCaptor.forClass(RecordAdminActionCommand.class);
        verify(audit, org.mockito.Mockito.times(2)).record(command.capture());
        assertThat(command.getAllValues())
                .extracting(RecordAdminActionCommand::targetType)
                .containsExactly(AdminTargetType.SUPPORT_NOTIFICATION, AdminTargetType.SUPPORT_TICKET);
    }
}
