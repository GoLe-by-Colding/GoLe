package com.gole.api.offer.adapter.out.id;

import com.gole.api.offer.application.port.out.OfferIdGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** UUID 기반 제안 식별자 생성 어댑터. */
@Component
public class OfferIdGenerator implements OfferIdGeneratorPort {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
