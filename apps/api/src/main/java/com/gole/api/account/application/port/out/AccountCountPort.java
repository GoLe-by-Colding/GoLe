package com.gole.api.account.application.port.out;

/** Outbound port: 전체 계정 수(추정치). */
public interface AccountCountPort {

    long estimatedAccountCount();
}
