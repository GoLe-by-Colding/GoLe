package com.gole.api.launch.application.port.out;

import com.gole.api.launch.domain.model.LaunchConfigChange;
import java.util.List;

/** Outbound port: 공개 설정 변경 이력. 추가와 조회만 있다(수정·삭제 없음). */
public interface LaunchConfigHistoryPort {

    void append(LaunchConfigChange change);

    List<LaunchConfigChange> findRecent(int limit);
}
