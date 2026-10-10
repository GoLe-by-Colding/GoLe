package com.gole.api.notification.application.service;

import com.gole.api.notification.application.port.in.NotificationAccountErasureUseCase;
import com.gole.api.notification.application.port.out.NotificationAccountErasurePort;
import org.springframework.stereotype.Service;

/** 회원 탈퇴 때 알림 기록 처리. 판정과 파기는 저장소 포트가 하고, 트랜잭션은 호출자(계정 파기)의 것을 쓴다. */
@Service
public class NotificationAccountErasureService implements NotificationAccountErasureUseCase {

    private final NotificationAccountErasurePort erasure;

    public NotificationAccountErasureService(NotificationAccountErasurePort erasure) {
        this.erasure = erasure;
    }

    @Override
    public NotificationErasure erase(String accountId, String anonymousSubject) {
        return erasure.erase(accountId, anonymousSubject);
    }
}
