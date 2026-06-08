package com.gole.api.account.application.port.out;

/**
 * 식별자 생성 outbound port.
 */
public interface IdentifierGeneratorPort {

    String newAccountId();
}
