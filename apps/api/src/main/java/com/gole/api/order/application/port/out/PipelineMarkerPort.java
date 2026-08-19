package com.gole.api.order.application.port.out;

/**
 * Outbound port: 파이프라인 1회성 액션 마커. (R7.3)
 *
 * <p>독촉 알림·예외 큐 등재처럼 상태 전이가 없는 액션은 유스케이스 멱등성의 보호를 받지
 * 못한다 — 마커가 없으면 스케줄러가 돌 때마다 같은 알림이 반복된다. 유니크 인덱스 삽입으로
 * "처음 한 번"만 통과시킨다.
 */
public interface PipelineMarkerPort {

    /**
     * (rule, refId) 마커를 남긴다.
     *
     * @return 처음 남긴 것이면 true, 이미 있으면 false
     */
    boolean markOnce(String rule, String refId);
}
