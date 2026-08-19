package com.gole.api.order.application.port.in;

/**
 * Inbound port: 구매자 분쟁 제기. (shipping-and-fees R4.1)
 */
public interface OpenDisputeUseCase {

    void open(OpenDisputeCommand command);

    /**
     * @param reasonKey {@code not_shipped | not_arrived | item_mismatch | damaged}
     * @param detail    상세 설명(선택)
     */
    record OpenDisputeCommand(String orderId, String buyerId, String reasonKey, String detail) {}
}
