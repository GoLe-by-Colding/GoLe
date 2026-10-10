package com.gole.api.notification.application.port.out;

import com.gole.api.notification.application.port.in.NotificationAccountErasureUseCase.NotificationErasure;

/** Outbound port: 회원 탈퇴 때 알림 기록의 차단 판정·파기를 저장소에서 한다. */
public interface NotificationAccountErasurePort {

    NotificationErasure erase(String accountId, String anonymousSubject);
}
