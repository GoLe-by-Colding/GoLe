package com.gole.api.common.operations;

import java.time.Duration;

/** 외부 운영 채널이 이벤트를 실제 수락했는지 동기적으로 확인하는 durable worker용 포트. */
public interface ConfirmedOperationalEventPublisher {

    DeliveryResult publishAndConfirm(OperationalEvent event);

    enum DeliveryStatus {
        DELIVERED,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE
    }

    /** errorCode는 비밀 URL·응답 본문·사용자 입력을 포함하지 않는 제한된 진단 코드다. */
    record DeliveryResult(DeliveryStatus status, String errorCode, Duration retryAfter) {

        public static DeliveryResult delivered() {
            return new DeliveryResult(DeliveryStatus.DELIVERED, null, null);
        }

        public static DeliveryResult retryable(String errorCode, Duration retryAfter) {
            return new DeliveryResult(DeliveryStatus.RETRYABLE_FAILURE, errorCode, retryAfter);
        }

        public static DeliveryResult permanent(String errorCode) {
            return new DeliveryResult(DeliveryStatus.PERMANENT_FAILURE, errorCode, null);
        }
    }
}
