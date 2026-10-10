package com.gole.api.media.application.port.in;

/**
 * Inbound port: 회원 탈퇴 때 미디어 컨텍스트가 맡는 일. (account-deletion-participants D1)
 *
 * <p>아직 파기하면 안 되는 기록이 남았는지 답하고, 파기할 때 자기 기록을 지우거나 익명 주체로 바꾼다. 호출자의
 * 트랜잭션 안에서 돌아 계정 파기와 함께 커밋되거나 함께 되돌려진다.
 */
public interface MediaAccountErasureUseCase {

    /** 회수되지 않은 미디어가 남아 있는가 — 파일 생애주기 검토 뒤 파기한다. */
    boolean hasLiveMedia(String accountId);

    /**
     * 이 계정의 기록을 지우거나 {@code anonymousSubject}로 바꾼다.
     *
     * @return 처리한 기록 수
     */
    MediaErasure erase(String accountId, String anonymousSubject);

    /**
     * @param revokedMediaAssets 회수된 미디어 소유자 가명화 수
     */
    record MediaErasure(long revokedMediaAssets) {}
}
