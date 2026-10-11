package com.gole.api.community.application.port.in;

/**
 * Inbound port: 회원 탈퇴 때 커뮤니티 컨텍스트가 맡는 일. (account-deletion-participants D1)
 *
 * <p>아직 파기하면 안 되는 기록이 남았는지 답하고, 파기할 때 자기 기록을 지우거나 익명 주체로 바꾼다. 호출자의
 * 트랜잭션 안에서 돌아 계정 파기와 함께 커밋되거나 함께 되돌려진다.
 */
public interface CommunityAccountErasureUseCase {

    /** 지우지 않은 글이나 숨기지 않은 댓글이 남아 있는가 — 공개 콘텐츠는 생애주기 검토 뒤 파기한다. */
    boolean hasPublicContent(String accountId);

    /**
     * 이 계정의 기록을 지우거나 {@code anonymousSubject}로 바꾼다.
     *
     * @return 처리한 기록 수
     */
    CommunityErasure erase(String accountId, String anonymousSubject);

    /**
     * @param deletedPosts 삭제된 글 가명화 수
     * @param hiddenComments 숨긴 댓글 가명화 수
     */
    record CommunityErasure(long deletedPosts, long hiddenComments) {}
}
