package com.gole.api.community.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.community.adapter.in.web.CommunityDtos.PostResponse;
import com.gole.api.community.application.port.in.GetFollowingPostFeedUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Community", description = "커뮤니티 피드·게시글·댓글·좋아요")
@RestController
@RequestMapping("/api/v1/community/feed")
public class FollowingCommunityFeedController {

    private final GetFollowingPostFeedUseCase followingFeed;

    public FollowingCommunityFeedController(GetFollowingPostFeedUseCase followingFeed) {
        this.followingFeed = followingFeed;
    }

    @Operation(summary = "팔로잉 커뮤니티 피드", description = "내가 팔로우한 빌더·판매자의 게시된 글을 최신순으로 조회합니다.")
    @GetMapping("/following")
    public List<PostResponse> following(@RequestParam(defaultValue = "50") int limit, HttpServletRequest request) {
        String viewerId = AuthenticatedUser.id(request);
        return followingFeed.feed(viewerId, limit).stream()
                .map(post -> PostResponse.from(post, viewerId))
                .toList();
    }
}
