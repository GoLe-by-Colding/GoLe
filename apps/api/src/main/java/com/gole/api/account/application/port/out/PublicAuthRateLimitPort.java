package com.gole.api.account.application.port.out;

import java.time.Duration;
import java.util.List;

/** 공개 인증 요청에 적용할 여러 Redis 시간창을 한 번에 원자적으로 획득한다. */
public interface PublicAuthRateLimitPort {

    Decision acquire(List<Bucket> buckets);

    record Bucket(String key, int maximum, Duration retention, Duration retryAfter) {
        public Bucket {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("rate limit key must not be blank");
            }
            if (maximum < 1) {
                throw new IllegalArgumentException("rate limit maximum must be positive");
            }
            requirePositive(retention, "retention");
            requirePositive(retryAfter, "retryAfter");
        }

        private static void requirePositive(Duration value, String name) {
            if (value == null || value.toMillis() < 1) {
                throw new IllegalArgumentException("rate limit " + name + " must be positive");
            }
        }
    }

    /** rejectedBucket은 거부한 buckets의 0 기반 인덱스이며, 허용 시 -1이다. */
    record Decision(boolean allowed, int rejectedBucket, Duration retryAfter) {
        public static Decision allowedDecision() {
            return new Decision(true, -1, Duration.ZERO);
        }

        public static Decision rejectedDecision(int rejectedBucket, Duration retryAfter) {
            if (rejectedBucket < 0) {
                throw new IllegalArgumentException("rejected bucket index must not be negative");
            }
            return new Decision(false, rejectedBucket, retryAfter);
        }
    }
}
