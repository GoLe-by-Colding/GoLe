package com.gole.api.review.application.port.out;

/**
 * 후기 식별자 생성 outbound port.
 */
public interface ReviewIdGeneratorPort {

    String newId();
}
