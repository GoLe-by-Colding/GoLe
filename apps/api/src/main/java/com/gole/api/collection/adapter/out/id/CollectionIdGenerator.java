package com.gole.api.collection.adapter.out.id;

import com.gole.api.collection.application.port.out.CollectionIdGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * UUID 기반 컬렉션 식별자 생성 어댑터.
 */
@Component
public class CollectionIdGenerator implements CollectionIdGeneratorPort {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
