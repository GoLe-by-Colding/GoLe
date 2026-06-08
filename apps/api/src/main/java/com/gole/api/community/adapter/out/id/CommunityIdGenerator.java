package com.gole.api.community.adapter.out.id;

import com.gole.api.community.application.port.out.CommunityIdGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * UUID 기반 커뮤니티 식별자 생성 어댑터.
 */
@Component
public class CommunityIdGenerator implements CommunityIdGeneratorPort {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
