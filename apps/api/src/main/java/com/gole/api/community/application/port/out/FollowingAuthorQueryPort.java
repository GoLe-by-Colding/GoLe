package com.gole.api.community.application.port.out;

import java.util.List;

/** 커뮤니티가 discovery 저장 기술을 알지 않고 팔로잉 작성자 목록을 읽는 경계. */
public interface FollowingAuthorQueryPort {

    List<String> findFollowingAuthorIds(String accountId);
}
