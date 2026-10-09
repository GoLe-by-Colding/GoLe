package com.gole.api.chat.application.port.out;

import java.util.function.Supplier;

/** Outbound port: 독립 트랜잭션 실행기. 저장소의 일시적 트랜잭션 충돌은 새 트랜잭션으로 몇 번 다시 시도한다. */
public interface RetryingTransactionPort {

    /**
     * {@code work} 를 새 트랜잭션(REQUIRES_NEW)에서 실행한다. 일시적 충돌이면 짧게 기다렸다가 다시 실행하고, 그 밖의 실패와
     * 커밋 결과를 알 수 없는 실패는 그대로 던진다 — 본문이 두 번 적용될 수 있어서다.
     *
     * @param operation 로그용 작업 이름
     * @param key 로그용 대상 식별자
     */
    <T> T inNewTransaction(String operation, String key, Supplier<T> work);
}
