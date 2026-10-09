package com.gole.api.bid.domain.exception;

/**
 * 같은 사용자·세트·상태의 {@code ACTIVE}가 동시에 들어와 유일 인덱스에 걸렸다.
 *
 * <p>HTTP로 나가는 오류가 아니다. 서비스가 잡아서 "이미 있으면 갱신" 경로로 다시 시도한다(D3).
 */
public class DuplicateActiveBidException extends RuntimeException {

    public DuplicateActiveBidException() {
        super("같은 세트·상태의 진행 중인 입찰이 동시에 생성됨");
    }
}
