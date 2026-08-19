package com.gole.api.order.application.service.pipeline;

import java.time.Instant;
import java.util.List;

/**
 * 파이프라인 규칙 = (만료 후보 조회, 건별 액션). (shipping-and-fees 설계 P3)
 *
 * <p>스케줄러가 규칙을 순회하며 후보마다 {@link #apply}를 호출하고, 예외를 건별로
 * 격리한다(R7.4). 액션은 <b>기존 유스케이스 호출로만</b> 구현한다(R7.3) — 멱등성은
 * 유스케이스의 상태 검사({@code OrderStateException} 등)가 이미 보장한다.
 */
public interface PipelineRule {

    /** 운영 이벤트·로그에 쓰는 규칙 이름. */
    String name();

    /** 지금 처리해야 할 대상 식별자 목록(주문 ID). 인덱스를 타는 제한 조회여야 한다. */
    List<String> candidates(Instant now);

    /**
     * 후보 1건 처리.
     *
     * @return 실제로 조치했으면 true (이미 처리된 건·조건 불충족은 false)
     */
    boolean apply(String orderId, Instant now);
}
