package com.gole.api.admin.application.port.out;

import com.gole.api.admin.domain.model.AdminTargetType;

/**
 * Outbound port: 감사 기록 대상 ID 가명화 저장소.
 *
 * <p>{@link AdminAuditPort} 는 추가·조회만 노출하는 계약이라 여기에 섞지 않는다. 개인정보 파기 때 대상 ID만 바꾸는 이 한 가지 변경을
 * 별도 포트로 드러내, 감사 로그를 고치는 경로가 어디에 있는지 숨지 않게 한다.
 */
public interface AdminAuditPseudonymizationPort {

    long replaceTargetId(AdminTargetType targetType, String fromTargetId, String toTargetId);
}
