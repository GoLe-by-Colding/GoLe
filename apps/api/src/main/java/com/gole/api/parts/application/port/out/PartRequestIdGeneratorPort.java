package com.gole.api.parts.application.port.out;

/** 부품 요청 식별자 생성 outbound port. */
public interface PartRequestIdGeneratorPort {

    String newId();
}
