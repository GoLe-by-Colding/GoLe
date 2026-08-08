package com.gole.api.community.application.port.in;

/**
 * Inbound port: 운영자의 게시글 강제 삭제. (admin-console 요구사항 5.2)
 *
 * <p>작성자 검증을 하는 {@link DeletePostUseCase}와 <b>의도적으로 별도 포트</b>로 둔다.
 * 권한 모델이 다르므로(작성자 vs 운영자) 하나의 메서드에 플래그로 섞지 않는다.
 * 호출 측(관리자 어댑터)에서 ADMIN 권한을 이미 강제하며, 사유는 감사 로그에 기록된다.
 */
public interface ModeratePostUseCase {

    void removeByModerator(String postId, String reason);
}
