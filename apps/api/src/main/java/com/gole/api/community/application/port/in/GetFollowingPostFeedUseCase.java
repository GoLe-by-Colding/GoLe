package com.gole.api.community.application.port.in;

import com.gole.api.community.domain.model.Post;
import java.util.List;

/** 사용자가 팔로우한 빌더·판매자의 게시글 피드를 조회한다. */
public interface GetFollowingPostFeedUseCase {

    List<Post> feed(String accountId, int limit);
}
