package com.gole.api.review.adapter.out.id;

import com.gole.api.review.application.port.out.ReviewIdGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * UUID 기반 후기 식별자 생성 어댑터.
 */
@Component
public class ReviewIdGenerator implements ReviewIdGeneratorPort {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
