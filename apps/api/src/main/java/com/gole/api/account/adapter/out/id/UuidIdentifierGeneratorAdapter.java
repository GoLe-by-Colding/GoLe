package com.gole.api.account.adapter.out.id;

import com.gole.api.account.application.port.out.IdentifierGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * UUID 기반 식별자 생성 어댑터.
 */
@Component
public class UuidIdentifierGeneratorAdapter implements IdentifierGeneratorPort {

    @Override
    public String newAccountId() {
        return UUID.randomUUID().toString();
    }
}
