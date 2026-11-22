package com.gole.api.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.community.application.port.out.BlockedAuthorQueryPort;
import com.gole.api.community.application.port.out.FollowingAuthorQueryPort;
import com.gole.api.community.application.port.out.PostRepositoryPort;
import com.gole.api.community.domain.model.Post;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FollowingPostFeedServiceTest {

    private final FollowingAuthorQueryPort following = mock(FollowingAuthorQueryPort.class);
    private final PostRepositoryPort posts = mock(PostRepositoryPort.class);
    private final BlockedAuthorQueryPort blocks = mock(BlockedAuthorQueryPort.class);
    private final FollowingPostFeedService service = new FollowingPostFeedService(following, posts, blocks);

    @Test
    void returnsNoRowsWithoutFollowingAuthorsAndSkipsPostQuery() {
        when(following.findFollowingAuthorIds("account-1")).thenReturn(List.of());

        assertThat(service.feed("account-1", 50)).isEmpty();

        verify(posts, never()).findPublishedByAuthorIdsRecentFirst(anyList(), eq(50));
    }

    @Test
    void deduplicatesAuthorsAndClampsLimitBeforeQueryingPosts() {
        Post post = mock(Post.class);
        when(following.findFollowingAuthorIds("account-1")).thenReturn(List.of("builder-1", "builder-1", "builder-2"));
        when(posts.findPublishedByAuthorIdsRecentFirst(List.of("builder-1", "builder-2"), 100))
                .thenReturn(List.of(post));

        assertThat(service.feed("account-1", 500)).containsExactly(post);
    }

    @Test
    @DisplayName("차단 관계인 팔로잉 작성자는 조회 대상에서 뺀다")
    void excludesBlockedAuthorsBeforeQueryingPosts() {
        Post post = mock(Post.class);
        when(following.findFollowingAuthorIds("account-1")).thenReturn(List.of("builder-B", "builder-C"));
        when(blocks.blockedCounterparts("account-1")).thenReturn(List.of("builder-B"));
        when(posts.findPublishedByAuthorIdsRecentFirst(List.of("builder-C"), 50))
                .thenReturn(List.of(post));

        assertThat(service.feed("account-1", 50)).containsExactly(post);
    }

    @Test
    @DisplayName("팔로잉 작성자가 모두 차단 관계면 글 조회를 건너뛴다")
    void skipsPostQueryWhenAllFollowingAuthorsAreBlocked() {
        when(following.findFollowingAuthorIds("account-1")).thenReturn(List.of("builder-B"));
        when(blocks.blockedCounterparts("account-1")).thenReturn(List.of("builder-B"));

        assertThat(service.feed("account-1", 50)).isEmpty();

        verify(posts, never()).findPublishedByAuthorIdsRecentFirst(anyList(), anyInt());
    }
}
