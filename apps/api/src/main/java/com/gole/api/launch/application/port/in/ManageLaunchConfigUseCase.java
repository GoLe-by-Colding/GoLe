package com.gole.api.launch.application.port.in;

import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchConfigChange;
import com.gole.api.launch.domain.model.LaunchFeature;
import com.gole.api.launch.domain.model.LaunchReadinessCheck;
import com.gole.api.launch.domain.model.LaunchStage;
import java.util.List;

/**
 * Inbound port: 관리자 전용 공개 설정 변경. 조회 포트와 분리해 권한 경계를 인터페이스로 드러낸다.
 */
public interface ManageLaunchConfigUseCase {

    /** 영속 안전 래치를 통과한 관리자 요청값. 실행값보다 높은 단계는 별도로 보존하지 않는다. */
    LaunchConfig requested();

    LaunchConfig changeStage(ChangeStageCommand command);

    /**
     * 단계 변경과 실제 변경 여부를 함께 반환한다.
     *
     * <p>같은 단계 재요청은 성공 응답이지만 변경은 아니다. 컨트롤러가 이 값을 사용해 실제
     * 상태 변경과 관리자 감사 로그를 1:1로 맞춘다.
     */
    default StageChangeResult changeStageWithResult(ChangeStageCommand command) {
        return new StageChangeResult(changeStage(command), true);
    }

    LaunchConfig setFeatureOverride(SetFeatureOverrideCommand command);

    ReadinessChangeResult setReadinessCheck(SetReadinessCheckCommand command);

    List<LaunchConfigChange> history(int limit);

    /**
     * @param reason 필수. 단계 변경은 서비스 전체를 열고 닫는 조치라 사유 없이 남기지 않는다.
     */
    record ChangeStageCommand(LaunchStage stage, String reason, String actorId, String actorEmail) {}

    record StageChangeResult(LaunchConfig config, boolean changed) {}

    record ReadinessChangeResult(LaunchConfig config, boolean changed, boolean safetyLowered) {}

    /**
     * @param enabled {@code null} 이면 override 를 해제하고 단계 기본값으로 되돌린다.
     */
    record SetFeatureOverrideCommand(
            LaunchFeature feature, Boolean enabled, String reason, String actorId, String actorEmail) {}

    record SetReadinessCheckCommand(
            LaunchReadinessCheck check, boolean confirmed, String reason, String actorId, String actorEmail) {}
}
