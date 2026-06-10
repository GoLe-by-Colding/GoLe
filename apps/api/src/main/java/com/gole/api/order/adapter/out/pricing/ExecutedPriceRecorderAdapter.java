package com.gole.api.order.adapter.out.pricing;

import com.gole.api.order.application.port.out.ExecutedPriceRecorderPort;
import com.gole.api.pricing.application.port.in.RecordExecutedPriceUseCase;
import com.gole.api.pricing.application.port.in.RecordExecutedPriceUseCase.RecordExecutedPriceCommand;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 가격(pricing) 컨텍스트 통합 어댑터. 주문 완료 시 체결가를 기록한다. (요구사항 9.1)
 */
@Component
public class ExecutedPriceRecorderAdapter implements ExecutedPriceRecorderPort {

    private final RecordExecutedPriceUseCase recordExecutedPrice;

    public ExecutedPriceRecorderAdapter(RecordExecutedPriceUseCase recordExecutedPrice) {
        this.recordExecutedPrice = recordExecutedPrice;
    }

    @Override
    public void record(String setNumber, long price, int quantity, Instant executedAt) {
        recordExecutedPrice.record(new RecordExecutedPriceCommand(setNumber, price, quantity, executedAt));
    }
}
