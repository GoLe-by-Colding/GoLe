package com.gole.api.parts.application.port.in;

/**
 * Inbound port: 회원 탈퇴 때 부품 요청 컨텍스트가 맡는 일. (account-deletion-participants D1)
 *
 * <p>파기할 때 자기 기록을 지우거나 익명 주체로 바꾼다. 호출자의
 * 트랜잭션 안에서 돌아 계정 파기와 함께 커밋되거나 함께 되돌려진다.
 */
public interface PartsAccountErasureUseCase {

    /**
     * 이 계정의 기록을 지우거나 {@code anonymousSubject}로 바꾼다.
     *
     * @return 처리한 기록 수
     */
    PartsErasure erase(String accountId, String anonymousSubject);

    /**
     * @param partRequests 부품 요청 삭제 수
     */
    record PartsErasure(long partRequests) {}
}
