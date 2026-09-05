package com.gole.api.notification.application.port.in;

import com.gole.api.notification.domain.model.DevicePlatform;

/**
 * Inbound port: 단말 푸시 토큰 등록·해제. (모바일 앱 스펙 R8.1)
 */
public interface RegisterDeviceTokenUseCase {

    /** 멱등하다. 같은 토큰을 다시 등록하면 소유 계정과 시각만 갱신된다. */
    void register(RegisterDeviceTokenCommand command);

    /**
     * 등록을 해제한다. 로그아웃 시 호출한다.
     *
     * <p>토큰만으로 지운다 — 계정을 함께 맞춰야 지워진다면, 계정이 바뀐 단말(기기 양도)의
     * 죽은 토큰이 영원히 남는다.
     */
    void unregister(String token);

    record RegisterDeviceTokenCommand(String accountId, String token, DevicePlatform platform) {}
}
