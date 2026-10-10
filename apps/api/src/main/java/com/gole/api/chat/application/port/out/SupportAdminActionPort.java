package com.gole.api.chat.application.port.out;

import com.gole.api.chat.domain.model.SupportAdminAction;
import com.gole.api.chat.domain.model.SupportOperator;

/**
 * Outbound port: 문의 콘솔 조치를 관리자 감사 로그에 남긴다. 조치가 성공한 뒤, 같은 트랜잭션 안에서 부른다 —
 * 감사 기록이 실패하면 조치도 함께 되돌린다.
 *
 * @param targetId 문의 방 ID, 알림 재큐잉은 이벤트 ID
 * @param detail 사람이 읽을 부가 정보. 없으면 {@code null}
 */
public interface SupportAdminActionPort {

    void record(SupportOperator operator, SupportAdminAction action, String targetId, String detail);
}
