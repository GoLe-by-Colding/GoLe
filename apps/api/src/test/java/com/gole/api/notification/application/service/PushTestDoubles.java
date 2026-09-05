package com.gole.api.notification.application.service;

import com.gole.api.notification.application.port.out.DeviceTokenRepositoryPort;
import com.gole.api.notification.application.port.out.PushSenderPort;
import com.gole.api.notification.domain.model.DeviceToken;
import java.util.ArrayList;
import java.util.List;

/** 푸시 경로 테스트용 가짜 포트. */
final class PushTestDoubles {
    private PushTestDoubles() {}
}

/** 보낸 메시지를 기록하고, 지정한 토큰에 대해 원하는 결과를 돌려준다. */
class RecordingPushSender implements PushSenderPort {

    final List<PushMessage> sent = new ArrayList<>();
    private final List<String> invalidTokens = new ArrayList<>();
    private RuntimeException failure;

    void markInvalid(String token) {
        invalidTokens.add(token);
    }

    /** 어댑터가 예외를 던지는 상황(계약 위반)을 흉내 낸다. */
    void throwOnSend(RuntimeException e) {
        this.failure = e;
    }

    @Override
    public PushOutcome send(PushMessage message) {
        sent.add(message);
        if (failure != null) {
            throw failure;
        }
        return invalidTokens.contains(message.token()) ? PushOutcome.TOKEN_INVALID : PushOutcome.ACCEPTED;
    }
}

class InMemoryDeviceTokens implements DeviceTokenRepositoryPort {

    final List<DeviceToken> rows = new ArrayList<>();

    @Override
    public void upsert(DeviceToken deviceToken) {
        rows.removeIf(r -> r.getToken().equals(deviceToken.getToken()));
        rows.add(deviceToken);
    }

    @Override
    public void deleteByToken(String token) {
        rows.removeIf(r -> r.getToken().equals(token));
    }

    @Override
    public List<DeviceToken> findByAccountId(String accountId) {
        return rows.stream().filter(r -> r.getAccountId().equals(accountId)).toList();
    }
}
