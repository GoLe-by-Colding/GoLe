package com.gole.api.community.application.port.out;

/**
 * 커뮤니티 식별자 생성 outbound port. 게시글/댓글 식별자에 공통으로 사용한다.
 */
public interface CommunityIdGeneratorPort {

    String newId();
}
