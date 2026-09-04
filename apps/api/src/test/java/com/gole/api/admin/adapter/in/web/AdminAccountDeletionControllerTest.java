package com.gole.api.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.in.ManageAccountDeletionRequestsUseCase;
import com.gole.api.account.application.port.in.ManageAccountDeletionRequestsUseCase.Result;
import com.gole.api.account.domain.model.AccountDeletionStatus;
import com.gole.api.admin.adapter.in.web.AdminAccountDeletionController.CompletionRequest;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

class AdminAccountDeletionControllerTest {

    @Test
    void completedPurgeAuditsOnlyOpaqueRequestReference() {
        ManageAccountDeletionRequestsUseCase deletions = mock(ManageAccountDeletionRequestsUseCase.class);
        RecordAdminActionUseCase audit = mock(RecordAdminActionUseCase.class);
        AdminAccountDeletionController controller = new AdminAccountDeletionController(deletions, audit);
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setAttribute(AdminAuthInterceptor.ATTR_ACCOUNT_ID, "admin-1");
        http.setAttribute(AdminAuthInterceptor.ATTR_ACCOUNT_EMAIL, "admin@gole.test");
        when(deletions.complete(any()))
                .thenReturn(new Result(
                        "request-opaque",
                        AccountDeletionStatus.COMPLETED,
                        List.of(),
                        null,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Map.of("accounts", 1L)));

        controller.complete(
                "request-opaque",
                new CompletionRequest("request-opaque", true),
                "550e8400-e29b-41d4-a716-446655440000",
                http);

        ArgumentCaptor<RecordAdminActionCommand> captured = ArgumentCaptor.forClass(RecordAdminActionCommand.class);
        verify(audit).record(captured.capture());
        assertThat(captured.getValue().type()).isEqualTo(AdminActionType.ACCOUNT_DELETION_COMPLETE);
        assertThat(captured.getValue().targetType()).isEqualTo(AdminTargetType.ACCOUNT_DELETION_REQUEST);
        assertThat(captured.getValue().targetId()).isEqualTo("request-opaque");
        assertThat(captured.getValue().reason()).isNull();
    }
}
