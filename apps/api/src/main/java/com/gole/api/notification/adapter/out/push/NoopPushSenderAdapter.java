package com.gole.api.notification.adapter.out.push;

import com.gole.api.notification.application.port.out.PushSenderPort;

/**
 * 푸시가 구성되지 않은 환경의 기본 어댑터.
 *
 * <p><b>기동을 막지 않는다.</b> 결제 설정은 없으면 기동을 거부하지만(무료 승인 사고를 막아야
 * 하므로) 푸시는 반대다 — 닫혀 있어도 서비스는 정상이고, 로컬·CI에서 자격증명을 요구하면
 * 개발이 막힌다. (R8.5)
 */
public final class NoopPushSenderAdapter implements PushSenderPort {

    @Override
    public PushOutcome send(PushMessage message) {
        return PushOutcome.DISABLED;
    }
}
