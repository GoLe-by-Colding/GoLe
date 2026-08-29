package com.gole.api.launch.application.port.in;

import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchConfigChange;
import com.gole.api.launch.domain.model.LaunchFeature;
import com.gole.api.launch.domain.model.LaunchStage;
import java.util.List;

/**
 * Inbound port: 관리자 전용 공개 설정 변경. 조회 포트와 분리해 권한 경계를 인터페이스로 드러낸다.
 */
public interface ManageLaunchConfigUseCase {

    /** 운영자가 저장해 둔 요청값. 실제 실행값은 정산 모드에 따라 더 낮게 잠길 수 있다. */
    LaunchConfig requested();

    LaunchConfig changeStage(ChangeStageCommand command);

    LaunchConfig setFeatureOverride(SetFeatureOverrideCommand command);

    List<LaunchConfigChange> history(int limit);

    /**
     * @param reason 필수. 단계 변경은 서비스 전체를 열고 닫는 조치라 사유 없이 남기지 않는다.
     */
    record ChangeStageCommand(LaunchStage stage, String reason, String actorId, String actorEmail) {}

    /**
     * @param enabled {@code null} 이면 override 를 해제하고 단계 기본값으로 되돌린다.
     */
    record SetFeatureOverrideCommand(
            LaunchFeature feature, Boolean enabled, String reason, String actorId, String actorEmail) {}
}
