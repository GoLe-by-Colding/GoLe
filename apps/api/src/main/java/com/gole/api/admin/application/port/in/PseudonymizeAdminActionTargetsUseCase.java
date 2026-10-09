package com.gole.api.admin.application.port.in;

import com.gole.api.admin.domain.model.AdminTargetType;

/**
 * Inbound port: 감사 기록의 대상 ID 가명화. 개인정보 파기로 대상(예: 문의 대화)이 사라질 때, 감사 기록이 파기된 대상 ID를
 * 계속 가리키지 않게 파기 영수증 ID로 바꾼다.
 *
 * <p>감사 로그에 허용하는 유일한 변경이다. 행위 유형·조치자·시각·사유는 그대로 남고 지우지 않는다. 호출자의 트랜잭션 안에서
 * 돌아 파기와 함께 커밋되거나 함께 되돌려진다.
 */
public interface PseudonymizeAdminActionTargetsUseCase {

    /** @return 대상 ID를 바꾼 감사 기록 수 */
    long replaceTargetId(AdminTargetType targetType, String fromTargetId, String toTargetId);
}
