package com.gole.api.bid.adapter.out.id;

import com.gole.api.bid.application.port.out.BidIdGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** UUID 기반 입찰 식별자 생성 어댑터. */
@Component
public class BidIdGenerator implements BidIdGeneratorPort {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
