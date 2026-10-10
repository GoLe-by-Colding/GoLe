package com.gole.api.pricing.application.service;

import com.gole.api.pricing.application.port.in.CountPriceTransactionsUseCase;
import com.gole.api.pricing.application.port.out.PriceTransactionCountPort;
import org.springframework.stereotype.Service;

/** 운영 대시보드용 체결 기록 수. */
@Service
public class PriceTransactionCountService implements CountPriceTransactionsUseCase {

    private final PriceTransactionCountPort counts;

    public PriceTransactionCountService(PriceTransactionCountPort counts) {
        this.counts = counts;
    }

    @Override
    public long estimatedPriceTransactionCount() {
        return counts.estimatedPriceTransactionCount();
    }
}
