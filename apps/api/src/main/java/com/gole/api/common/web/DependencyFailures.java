package com.gole.api.common.web;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;

/**
 * 외부 인프라(DB·캐시·오브젝트 스토리지 등) 연결 장애 보고. 오류 참조값을 만들어 서버 로그와 운영 알림에 함께 남기고, 사용자 응답에는 그
 * 참조값만 싣는다.
 *
 * <p>공통 예외 처리기와 각 컨텍스트의 예외 처리기가 같은 형식으로 보고하도록 한 곳에 둔다.
 */
public final class DependencyFailures {

    private DependencyFailures() {}

    /** @return 사용자 응답에 붙일 오류 참조값 */
    public static String publish(
            OperationalEventPublisher publisher, Logger log, String title, Exception ex, HttpServletRequest request) {
        String errorReference = UUID.randomUUID().toString();
        log.error("[{}] {} path={}", errorReference, title, request.getRequestURI(), ex);
        publisher.publish(new OperationalEvent(
                Category.APPLICATION,
                Level.ERROR,
                title,
                "외부 인프라 연결 실패를 감지했습니다. 서버 로그에서 원인을 확인하고 연결 상태를 복구하세요.",
                Map.of(
                        "오류 참조", errorReference,
                        "요청 경로", request.getRequestURI(),
                        "예외 종류", ex.getClass().getSimpleName()),
                Instant.now()));
        return errorReference;
    }
}
