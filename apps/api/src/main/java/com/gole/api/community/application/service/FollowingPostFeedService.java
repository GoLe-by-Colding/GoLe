package com.gole.api.community.application.service;

import com.gole.api.community.application.port.in.GetFollowingPostFeedUseCase;
import com.gole.api.community.application.port.out.FollowingAuthorQueryPort;
import com.gole.api.community.application.port.out.PostRepositoryPort;
import com.gole.api.community.domain.model.Post;
import java.util.List;
import org.springframework.stereotype.Service;

/** 팔로잉 피드를 서버에서 직접 조회해 전체 피드 첫 페이지에 없는 글도 누락하지 않는다. */
@Service
public class FollowingPostFeedService implements GetFollowingPostFeedUseCase {

    private static final int MAX_FEED_ROWS = 100;

    private final FollowingAuthorQueryPort followingAuthors;
    private final PostRepositoryPort posts;

    public FollowingPostFeedService(FollowingAuthorQueryPort followingAuthors, PostRepositoryPort posts) {
        this.followingAuthors = followingAuthors;
        this.posts = posts;
    }

    @Override
    public List<Post> feed(String accountId, int limit) {
        List<String> authorIds = followingAuthors.findFollowingAuthorIds(accountId).stream()
                .distinct()
                .toList();
        if (authorIds.isEmpty()) {
            return List.of();
        }
        return posts.findPublishedByAuthorIdsRecentFirst(authorIds, clamp(limit));
    }

    private static int clamp(int requested) {
        return Math.max(1, Math.min(requested, MAX_FEED_ROWS));
    }
}
