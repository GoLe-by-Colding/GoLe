package com.gole.api.notification.application.service;

import com.gole.api.notification.application.port.out.DeviceTokenRepositoryPort;
import com.gole.api.notification.application.port.out.PushSenderPort;
import com.gole.api.notification.application.port.out.PushSenderPort.PushMessage;
import com.gole.api.notification.application.port.out.PushSenderPort.PushOutcome;
import com.gole.api.notification.domain.model.DeviceToken;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 알림 한 건을 수신자의 모든 단말로 밀어 넣는다.
 *
 * <p><b>주 흐름과 스레드를 나눈다.</b> FCM은 네트워크 호출이고, 알림 발행은 주문 확정·채팅
 * 전송 같은 트랜잭션 끝에 붙는다. 같은 스레드에서 보내면 외부 서비스의 지연이 그대로 거래
 * 응답 시간이 된다. 실패를 삼키는 것만으로는 그 문제가 해결되지 않는다 — 느린 성공도 문제다.
 *
 * <p>죽은 토큰은 발견 즉시 지운다. 앱 삭제·재설치로 토큰은 계속 죽는데, 두면 매 알림마다
 * 실패하는 호출이 쌓이기만 한다.
 */
@Component
public class PushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);

    private final DeviceTokenRepositoryPort tokens;
    private final PushSenderPort pushSender;
    private final Executor executor;

    public PushDispatcher(DeviceTokenRepositoryPort tokens, PushSenderPort pushSender, Executor pushExecutor) {
        this.tokens = tokens;
        this.pushSender = pushSender;
        this.executor = pushExecutor;
    }

    /** 호출 즉시 반환한다. 반환은 발송을 뜻하지 않는다. */
    public void dispatch(String recipientId, String title, String body, String link) {
        executor.execute(() -> {
            try {
                deliver(recipientId, title, body, link);
            } catch (RuntimeException e) {
                // 여기서 새어 나가면 실행기 스레드가 죽는다. 푸시 실패로 서비스가 흔들리지 않게 한다.
                log.warn("푸시 발송 중 예기치 못한 오류 recipientId={}", recipientId, e);
            }
        });
    }

    private void deliver(String recipientId, String title, String body, String link) {
        List<DeviceToken> targets = tokens.findByAccountId(recipientId);
        for (DeviceToken target : targets) {
            PushOutcome outcome =
                    pushSender.send(new PushMessage(target.getToken(), target.getPlatform(), title, body, link));
            if (outcome == PushOutcome.TOKEN_INVALID) {
                tokens.deleteByToken(target.getToken());
                log.info("죽은 푸시 토큰을 제거했다 recipientId={} platform={}", recipientId, target.getPlatform());
            }
        }
    }
}
