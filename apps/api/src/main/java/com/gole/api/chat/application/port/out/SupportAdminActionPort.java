package com.gole.api.chat.application.port.out;

import com.gole.api.chat.domain.model.SupportAdminAction;
import com.gole.api.chat.domain.model.SupportOperator;

/**
 * Outbound port: 문의 콘솔 조치를 관리자 감사 로그에 남긴다. 조치가 성공한 뒤, 같은 트랜잭션 안에서 부른다.
 *
 * <p>같은 트랜잭션이라 조치가 되돌려지면 감사 기록도 함께 되돌려져 거짓 감사가 남지 않는다. 반대로 감사 기록 자체가
 * 실패하면 이미 성공한 조치는 되돌리지 않고 오류 로그만 남긴다(admin-console 요구사항 8.5,
 * {@code AdminAuditService.record}).
 *
 * @param targetId 문의 방 ID, 알림 재큐잉은 이벤트 ID
 * @param detail 사람이 읽을 부가 정보. 없으면 {@code null}
 */
public interface SupportAdminActionPort {

    void record(SupportOperator operator, SupportAdminAction action, String targetId, String detail);
}
