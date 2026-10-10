package com.gole.api.admin.application.port.in;

import com.gole.api.admin.domain.model.AdminTargetType;

/**
 * Inbound port: 감사 기록의 대상 ID 가명화. 개인정보 파기로 대상(예: 문의 대화)이 사라질 때, 감사 기록이 파기된 대상 ID를
 * 계속 가리키지 않게 파기 영수증 ID로 바꾼다.
 *
 * <p>감사 로그에 허용하는 변경은 이 둘뿐이다. 행위 유형·조치자·시각은 그대로 남는다. 호출자의 트랜잭션 안에서 돌아 파기와 함께
 * 커밋되거나 함께 되돌려진다.
 */
public interface PseudonymizeAdminActionTargetsUseCase {

    /**
     * 대상 ID만 바꾸고 사유는 남긴다(문의 대화 파기).
     *
     * @return 대상 ID를 바꾼 감사 기록 수
     */
    long replaceTargetId(AdminTargetType targetType, String fromTargetId, String toTargetId);

    /**
     * 대상 ID를 바꾸고 사유도 지운다(회원 탈퇴). 회원 조치 사유에는 그 회원을 다시 식별할 정보가 들어갈 수 있다.
     * (account-deletion-participants D4)
     *
     * @return 대상 ID를 바꾼 감사 기록 수
     */
    long replaceTargetIdAndDropReason(AdminTargetType targetType, String fromTargetId, String toTargetId);
}
