package com.gole.api.parts.application.port.out;

/**
 * 세트 보유자에게 부품 요청 소식을 보내는 outbound port. notification 컨텍스트로 구현한다. (W8)
 *
 * <p>best-effort다. 구현은 발송 실패를 삼켜야 한다 — 알림 한 건 실패가 요청 등록이나 다른 보유자
 * 알림을 막으면 안 된다.
 */
public interface PartRequestHolderNotifierPort {

    /** @param setLabel 알림 문구에 쓰는 세트 표시(예: "에펠탑(10307)") */
    void notifyOwner(String recipientId, String requestId, String setLabel);
}
