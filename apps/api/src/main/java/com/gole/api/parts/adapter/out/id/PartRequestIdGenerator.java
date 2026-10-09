package com.gole.api.parts.adapter.out.id;

import com.gole.api.parts.application.port.out.PartRequestIdGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** UUID 기반 부품 요청 식별자 생성 어댑터. */
@Component
public class PartRequestIdGenerator implements PartRequestIdGeneratorPort {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
