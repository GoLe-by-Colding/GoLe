package com.gole.api.catalog.application.service;

import com.gole.api.catalog.application.port.in.CountLegoSetsUseCase;
import com.gole.api.catalog.application.port.out.LegoSetCountPort;
import org.springframework.stereotype.Service;

/** 운영 대시보드용 세트 수. */
@Service
public class LegoSetCountService implements CountLegoSetsUseCase {

    private final LegoSetCountPort counts;

    public LegoSetCountService(LegoSetCountPort counts) {
        this.counts = counts;
    }

    @Override
    public long estimatedLegoSetCount() {
        return counts.estimatedLegoSetCount();
    }
}
