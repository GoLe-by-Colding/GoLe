package com.gole.api.launch.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.admin.adapter.in.web.AdminAuthInterceptor;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.common.config.SellerIdentityVerificationProperties;
import com.gole.api.launch.adapter.in.web.LaunchDtos.ChangeStageRequest;
import com.gole.api.launch.adapter.in.web.LaunchDtos.ReadinessCheckRequest;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase.ReadinessChangeResult;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase.StageChangeResult;
import com.gole.api.launch.application.port.out.LaunchSettlementModePort;
import com.gole.api.launch.application.port.out.LaunchSettlementModePort.Mode;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchStage;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AdminLaunchControllerTest {

    private final GetLaunchConfigUseCase getLaunchConfig = mock(GetLaunchConfigUseCase.class);
    private final ManageLaunchConfigUseCase manageLaunchConfig = mock(ManageLaunchConfigUseCase.class);
    private final RecordAdminActionUseCase audit = mock(RecordAdminActionUseCase.class);
    private final LaunchSettlementModePort settlementMode = mock(LaunchSettlementModePort.class);
    private final SellerIdentityVerificationProperties sellerIdentityVerification =
            new SellerIdentityVerificationProperties();
    private final AdminLaunchController controller = new AdminLaunchController(
            getLaunchConfig, manageLaunchConfig, audit, settlementMode, sellerIdentityVerification);
    private final LaunchConfig browseOnly = new LaunchConfig(LaunchStage.BROWSE_ONLY, Map.of(), null, "admin-1");

    @BeforeEach
    void setUp() {
        when(getLaunchConfig.current()).thenReturn(browseOnly);
        when(settlementMode.currentMode()).thenReturn(Mode.DISABLED);
    }

    @Test
    @DisplayName("같은 단계 재요청은 관리자 감사 로그를 남기지 않는다")
    void sameStageNoopDoesNotRecordAdminAudit() {
        when(manageLaunchConfig.changeStageWithResult(any())).thenReturn(new StageChangeResult(browseOnly, false));

        controller.changeStage(new ChangeStageRequest(1, "현재 단계 확인"), adminRequest());

        verify(audit, never()).record(any());
    }

    @Test
    @DisplayName("실제 단계 변경만 관리자 감사 로그를 남긴다")
    void changedStageRecordsAdminAudit() {
        when(manageLaunchConfig.changeStageWithResult(any())).thenReturn(new StageChangeResult(browseOnly, true));

        controller.changeStage(new ChangeStageRequest(1, "커뮤니티 공개"), adminRequest());

        verify(audit).record(any());
    }

    @Test
    @DisplayName("운영 준비 확인이 실제로 바뀐 경우만 관리자 감사 로그를 남긴다")
    void readinessChangeRecordsAuditOnlyWhenChanged() {
        when(manageLaunchConfig.setReadinessCheck(any()))
                .thenReturn(new ReadinessChangeResult(browseOnly, true, false));

        controller.setReadiness("businessDisclosure", new ReadinessCheckRequest(true, "사업자 고지 확인"), adminRequest());

        verify(audit).record(any());
    }

    private static MockHttpServletRequest adminRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminAuthInterceptor.ATTR_ACCOUNT_ID, "admin-1");
        request.setAttribute(AdminAuthInterceptor.ATTR_ACCOUNT_EMAIL, "admin@gole.local");
        return request;
    }
}
