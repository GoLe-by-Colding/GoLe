package com.gole.api.notification.application.service;

import com.gole.api.notification.application.port.in.RegisterDeviceTokenUseCase;
import com.gole.api.notification.application.port.out.DeviceTokenRepositoryPort;
import com.gole.api.notification.domain.model.DeviceToken;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** 단말 토큰 등록·해제. (R8.1) */
@Service
public class DeviceTokenService implements RegisterDeviceTokenUseCase {

    private final DeviceTokenRepositoryPort repository;
    private final Clock clock;

    public DeviceTokenService(DeviceTokenRepositoryPort repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void register(RegisterDeviceTokenCommand command) {
        repository.upsert(
                new DeviceToken(command.token(), command.accountId(), command.platform(), Instant.now(clock)));
    }

    @Override
    public void unregister(String token) {
        repository.deleteByToken(token);
    }
}
