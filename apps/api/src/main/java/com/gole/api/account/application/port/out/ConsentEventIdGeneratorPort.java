package com.gole.api.account.application.port.out;

/**
 * Outbound port: 제3자 제공 동의 이벤트 식별자 생성.
 *
 * <p>같은 시각의 결정은 식별자 순서로 최신을 가르므로({@code occurredAt desc, _id desc}), 생성 순서대로 커지는 값이어야 한다.
 */
public interface ConsentEventIdGeneratorPort {

    String newConsentEventId();
}
