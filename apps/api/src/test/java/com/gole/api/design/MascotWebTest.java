package com.gole.api.design;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.account.domain.model.Role;
import com.gole.api.admin.adapter.in.web.AdminAuthInterceptor;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.common.web.auth.SessionCookie;
import com.gole.api.design.adapter.in.web.MascotController;
import com.gole.api.design.application.port.in.ManageMascotUseCase;
import com.gole.api.design.domain.model.ActiveMascot;
import com.gole.api.design.domain.model.MascotSelection;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MascotWebTest {
    final ManageMascotUseCase service = mock(ManageMascotUseCase.class);
    final RecordAdminActionUseCase audit = mock(RecordAdminActionUseCase.class);
    final GetCurrentSessionUseCase sessions = mock(GetCurrentSessionUseCase.class);

    MockMvc mvc() {
        when(sessions.resolve("")).thenReturn(Optional.empty());
        when(sessions.resolve("user")).thenReturn(Optional.of(new CurrentSession("user", "u@example.com", Role.USER)));
        when(sessions.resolve("admin"))
                .thenReturn(Optional.of(new CurrentSession("admin-1", "a@example.test", Role.ADMIN)));
        return MockMvcBuilders.standaloneSetup(new MascotController(service, audit))
                .setControllerAdvice(new GlobalExceptionHandler(
                        mock(com.gole.api.common.operations.OperationalEventPublisher.class)))
                .addMappedInterceptors(
                        new String[] {"/api/admin/**"}, new AdminAuthInterceptor(sessions, new SessionCookie("false")))
                .build();
    }

    @Test
    @DisplayName("공개 응답에는 적용한 사람·사유가 없고 에셋 모양만 있다")
    void publicResponseHasNoAuditFields() throws Exception {
        when(service.active())
                .thenReturn(new ActiveMascot(
                        7, "u-1", ActiveMascot.Kind.UPLOAD, "축제 고래", "/api/v1/media/images/a.png", null, 400, 300));
        mvc().perform(get("/api/v1/config/mascot"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.revision").value(7))
                .andExpect(jsonPath("$.asset.kind").value("UPLOAD"))
                .andExpect(jsonPath("$.asset.imageUrl").value("/api/v1/media/images/a.png"))
                .andExpect(jsonPath("$.actorId").doesNotExist())
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.asset.actorId").doesNotExist());
    }

    @Test
    @DisplayName("관리자 경로는 비로그인 401, 일반 회원 403이고 유스케이스에 닿지 않는다")
    void adminEndpointsDenyMissingAndUserSessions() throws Exception {
        var mvc = mvc();
        for (String path : new String[] {"/api/admin/mascot", "/api/admin/mascot/history"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", "Bearer user")).andExpect(status().isForbidden());
        }
        for (String path : new String[] {"/api/admin/mascot/assets", "/api/admin/mascot/publish"}) {
            mvc.perform(post(path).contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path)
                            .header("Authorization", "Bearer user")
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(delete("/api/admin/mascot/assets/u-1")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/mascot/assets/u-1").header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service, audit);
    }

    @Test
    @DisplayName("잘못된 적용·등록 요청은 유스케이스에 닿기 전에 400이다")
    void invalidBodiesAreRejectedBeforeUseCase() throws Exception {
        var mvc = mvc();
        mvc.perform(post("/api/admin/mascot/publish")
                        .header("Authorization", "Bearer admin")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":0,\"assetId\":\"baby-round\",\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/mascot/assets")
                        .header("Authorization", "Bearer admin")
                        .contentType("application/json")
                        .content("{\"name\":\"고래\",\"imageKey\":\"images/x.png\",\"width\":4,\"height\":64}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service, audit);
    }

    @Test
    @DisplayName("적용이 성공하면 조치자를 요청 속성에서 읽어 중앙 감사에 남기고, 실패하면 남기지 않는다")
    void publishRecordsAuditOnlyOnSuccess() throws Exception {
        var mvc = mvc();
        when(service.publish(0, "baby-round", "가을", "admin-1"))
                .thenReturn(
                        new MascotSelection(1, "baby-round", "둥근 아기 고래", "admin-1", "가을", "PUBLISH", Instant.EPOCH));
        mvc.perform(post("/api/admin/mascot/publish")
                        .header("Authorization", "Bearer admin")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":0,\"assetId\":\"baby-round\",\"reason\":\"가을\","
                                + "\"actorId\":\"forged\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1));
        var command = ArgumentCaptor.forClass(RecordAdminActionCommand.class);
        verify(audit).record(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().type()).isEqualTo(AdminActionType.MASCOT_PUBLISH);
        org.assertj.core.api.Assertions.assertThat(command.getValue().actorId()).isEqualTo("admin-1");

        reset(audit);
        when(service.publish(anyLong(), eq("tail-up"), any(), any()))
                .thenThrow(new ConflictException("MASCOT_REVISION_CONFLICT", "conflict"));
        mvc.perform(post("/api/admin/mascot/publish")
                        .header("Authorization", "Bearer admin")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":0,\"assetId\":\"tail-up\",\"reason\":\"r\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MASCOT_REVISION_CONFLICT"));
        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("삭제는 204이고 감사에 남긴다")
    void deleteReturnsNoContentAndAudits() throws Exception {
        mvc().perform(delete("/api/admin/mascot/assets/u-1").header("Authorization", "Bearer admin"))
                .andExpect(status().isNoContent());
        verify(service).delete("u-1", "admin-1");
        verify(audit)
                .record(argThat(c -> c.type() == AdminActionType.MASCOT_ASSET_DELETE
                        && c.targetId().equals("u-1")));
    }
}
