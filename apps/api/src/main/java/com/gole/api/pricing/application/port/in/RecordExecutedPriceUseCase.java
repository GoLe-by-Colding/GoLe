package com.gole.api.pricing.application.port.in;

import java.time.Instant;

/**
 * Inbound port: 체결가 기록. (요구사항 9.1)
 * 추후 Order 완료 시 Settlement/Trade가 호출한다.
 */
public interface RecordExecutedPriceUseCase {

    void record(RecordExecutedPriceCommand command);

    record RecordExecutedPriceCommand(
            String setNumber,
            long price,
            int quantity,
            Instant executedAt,
            String condition,
            String source,
            String sourceReference) {

        public RecordExecutedPriceCommand {
            if (("platform_payment".equalsIgnoreCase(source) || "platform_test".equalsIgnoreCase(source))
                    && (sourceReference == null || sourceReference.isBlank())) {
                throw new IllegalArgumentException("platform payment evidence requires an order reference");
            }
        }

        /** 주문 컨텍스트가 증명한 플랫폼 결제 체결. */
        public static RecordExecutedPriceCommand platformPayment(
                String setNumber, long price, int quantity, Instant executedAt, String condition, String orderId) {
            return new RecordExecutedPriceCommand(
                    setNumber, price, quantity, executedAt, condition, "platform_payment", orderId);
        }

        /** 테스트 채널·스텁 주문. 개발 UI에는 표시할 수 있지만 공개 시세에는 포함하지 않는다. */
        public static RecordExecutedPriceCommand platformTest(
                String setNumber, long price, int quantity, Instant executedAt, String condition, String orderId) {
            return new RecordExecutedPriceCommand(
                    setNumber, price, quantity, executedAt, condition, "platform_test", orderId);
        }

        /** 하위호환 — 참조 없는 기록은 검증되지 않은 레거시로 격리한다. */
        public RecordExecutedPriceCommand(
                String setNumber, long price, int quantity, Instant executedAt, String condition) {
            this(setNumber, price, quantity, executedAt, condition, "legacy_unverified", null);
        }

        /** 상태·출처 미지정 레거시 체결 — 공개 시세에는 기본적으로 포함하지 않는다. */
        public RecordExecutedPriceCommand(String setNumber, long price, int quantity, Instant executedAt) {
            this(setNumber, price, quantity, executedAt, null);
        }
    }
}
