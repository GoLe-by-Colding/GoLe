package com.gole.api.launch.application.port.out;

import com.gole.api.launch.domain.model.LaunchConfig;
import java.util.Optional;

/** Outbound port: 공개 설정 영속화. 단일 문서를 읽고 덮어쓴다. */
public interface LaunchConfigRepositoryPort {

    Optional<LaunchConfig> load();

    /** 저장 후 낙관적 잠금 버전이 반영된 값을 반환한다. */
    LaunchConfig save(LaunchConfig config);
}
