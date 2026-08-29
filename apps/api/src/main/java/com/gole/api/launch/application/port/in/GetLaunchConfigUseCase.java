package com.gole.api.launch.application.port.in;

import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchFeature;

/**
 * Inbound port: 현재 공개 설정 조회. 공개 API와 서버 내부 게이트가 함께 쓴다.
 *
 * <p>권한이 없는 조회이므로 사유·조치자 같은 운영 메타는 여기서 노출하지 않는다.
 */
public interface GetLaunchConfigUseCase {

    LaunchConfig current();

    /** 기능 단건 판정 — 게이트가 매 요청 부르는 경로라 별도로 둔다. */
    default boolean isEnabled(LaunchFeature feature) {
        return current().isEnabled(feature);
    }
}
