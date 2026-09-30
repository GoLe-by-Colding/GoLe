package com.gole.api.promotion.application.port.in;

/**
 * 관리자 버튼으로 발행 에이전트 실행을 시작한다. 무엇을 올릴지는 에이전트가 승인된 글 중에서 고른다.
 */
public interface RequestPromotionPublishRunUseCase {

    /** 승인된 글이 없으면 {@code NoApprovedPromotionPostsException}. */
    void requestPublishRun(String requestedBy);
}
