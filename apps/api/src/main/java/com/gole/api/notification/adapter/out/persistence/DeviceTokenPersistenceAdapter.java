package com.gole.api.notification.adapter.out.persistence;

import com.gole.api.notification.application.port.out.DeviceTokenRepositoryPort;
import com.gole.api.notification.domain.model.DevicePlatform;
import com.gole.api.notification.domain.model.DeviceToken;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 단말 푸시 토큰 영속성 어댑터. */
@Component
public class DeviceTokenPersistenceAdapter implements DeviceTokenRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(DeviceTokenPersistenceAdapter.class);

    private final DeviceTokenMongoRepository repository;

    public DeviceTokenPersistenceAdapter(DeviceTokenMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void upsert(DeviceToken deviceToken) {
        // 토큰이 _id이므로 save가 곧 upsert다.
        repository.save(new DeviceTokenDocument(
                deviceToken.getToken(),
                deviceToken.getAccountId(),
                deviceToken.getPlatform().name(),
                deviceToken.getRegisteredAt()));
    }

    @Override
    public void deleteByToken(String token) {
        repository.deleteById(token);
    }

    @Override
    public List<DeviceToken> findByAccountId(String accountId) {
        return repository.findByAccountId(accountId).stream()
                .map(this::toDomain)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private DeviceToken toDomain(DeviceTokenDocument document) {
        DevicePlatform platform;
        try {
            platform = DevicePlatform.valueOf(document.getPlatform());
        } catch (IllegalArgumentException | NullPointerException unknownPlatform) {
            // 저장된 값이 열거형에서 사라진 경우. 발송 경로가 통째로 죽는 것보다 그 단말만 건너뛴다.
            log.warn("알 수 없는 단말 플랫폼 '{}' — 이 토큰은 건너뛴다", document.getPlatform());
            return null;
        }
        return new DeviceToken(document.getToken(), document.getAccountId(), platform, document.getRegisteredAt());
    }
}
