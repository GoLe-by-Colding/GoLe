package com.gole.api.order.adapter.out.pricing;

import com.gole.api.order.application.port.out.ExecutedPriceRecorderPort;
import com.gole.api.order.domain.model.PaymentEvidenceKind;
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
    public void record(
            String orderId,
            String setNumber,
            long price,
            int quantity,
            Instant executedAt,
            String condition,
            PaymentEvidenceKind paymentEvidenceKind) {
        RecordExecutedPriceCommand command;
        if (paymentEvidenceKind == PaymentEvidenceKind.LIVE) {
            command = RecordExecutedPriceCommand.platformPayment(
                    setNumber, price, quantity, executedAt, condition, orderId);
        } else if (paymentEvidenceKind == PaymentEvidenceKind.TEST) {
            command =
                    RecordExecutedPriceCommand.platformTest(setNumber, price, quantity, executedAt, condition, orderId);
        } else {
            command = new RecordExecutedPriceCommand(
                    setNumber, price, quantity, executedAt, condition, "legacy_unverified", orderId);
        }
        recordExecutedPrice.record(command);
    }
}
