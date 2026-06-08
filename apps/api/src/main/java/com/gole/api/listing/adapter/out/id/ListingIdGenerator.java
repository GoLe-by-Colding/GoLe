package com.gole.api.listing.adapter.out.id;

import com.gole.api.listing.application.port.out.ListingIdGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * UUID 기반 리스팅 식별자 생성 어댑터.
 */
@Component
public class ListingIdGenerator implements ListingIdGeneratorPort {

    @Override
    public String newListingId() {
        return UUID.randomUUID().toString();
    }
}
