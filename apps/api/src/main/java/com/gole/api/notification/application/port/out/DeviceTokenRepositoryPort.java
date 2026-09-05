package com.gole.api.notification.application.port.out;

import com.gole.api.notification.domain.model.DeviceToken;
import java.util.List;

/** Outbound port: 단말 푸시 토큰 영속성. */
public interface DeviceTokenRepositoryPort {

    /** 토큰을 키로 덮어쓴다. 재등록이 행을 늘리지 않는다. */
    void upsert(DeviceToken deviceToken);

    void deleteByToken(String token);

    List<DeviceToken> findByAccountId(String accountId);
}
