package com.gole.api.notification.application.port.out;

import com.gole.api.notification.domain.model.DevicePlatform;

/**
 * Outbound port: 단말 푸시 발송.
 *
 * <p><b>예외를 던지지 않는다.</b> 푸시는 주문·채팅 트랜잭션에 딸린 부수 효과이지 성공 조건이
 * 아니다(R8.3). 실패를 예외로 표현하면 호출부마다 try/catch 규율에 기대게 되고, 한 곳만
 * 빠뜨려도 알림 발행이 주 흐름을 무너뜨린다. 그래서 결과를 값으로 돌려준다.
 */
public interface PushSenderPort {

    PushOutcome send(PushMessage message);

    /**
     * @param token    FCM 등록 토큰
     * @param platform 페이로드 분기용
     * @param title    알림 제목
     * @param body     알림 본문
     * @param link     탭했을 때 열 앱 내 경로(nullable). 데이터 페이로드로 실린다.
     */
    record PushMessage(String token, DevicePlatform platform, String title, String body, String link) {}

    enum PushOutcome {
        /** FCM이 접수했다. 단말 수신을 보장하지는 않는다. */
        ACCEPTED,
        /** 토큰이 더 이상 유효하지 않다. 호출부는 이 토큰을 지워야 한다. */
        TOKEN_INVALID,
        /** 일시적 실패. 토큰은 살려 둔다. */
        FAILED,
        /** 푸시가 구성되지 않았다. 오류가 아니다. */
        DISABLED
    }
}
