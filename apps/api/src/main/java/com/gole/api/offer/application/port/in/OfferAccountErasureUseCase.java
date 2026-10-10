package com.gole.api.offer.application.port.in;

/**
 * Inbound port: 회원 탈퇴 때 가격 제안 컨텍스트가 맡는 일. (account-deletion-participants D1)
 *
 * <p>파기할 때 자기 기록을 지우거나 익명 주체로 바꾼다. 호출자의
 * 트랜잭션 안에서 돌아 계정 파기와 함께 커밋되거나 함께 되돌려진다.
 */
public interface OfferAccountErasureUseCase {

    /**
     * 이 계정의 기록을 지우거나 {@code anonymousSubject}로 바꾼다.
     *
     * @return 처리한 기록 수
     */
    OfferErasure erase(String accountId, String anonymousSubject);

    /**
     * @param offers 가격 제안 삭제 수
     */
    record OfferErasure(long offers) {}
}
