package com.gole.api.account.application.port.in;

/** Inbound port: 운영 대시보드에 보일 전체 계정 수. */
public interface CountAccountsUseCase {

    /** 전체 계정 수(추정치 — 컬렉션 메타데이터로 센다). */
    long estimatedAccountCount();
}
