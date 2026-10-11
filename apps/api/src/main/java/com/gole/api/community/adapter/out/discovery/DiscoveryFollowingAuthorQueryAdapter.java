package com.gole.api.community.adapter.out.discovery;

import com.gole.api.community.application.port.out.FollowingAuthorQueryPort;
import com.gole.api.discovery.application.port.in.FollowSellerUseCase;
import java.util.List;
import org.springframework.stereotype.Component;

/** discovery 팔로우 컨텍스트를 커뮤니티 피드 포트에 연결한다. */
@Component
public class DiscoveryFollowingAuthorQueryAdapter implements FollowingAuthorQueryPort {

    private final FollowSellerUseCase follows;

    public DiscoveryFollowingAuthorQueryAdapter(FollowSellerUseCase follows) {
        this.follows = follows;
    }

    @Override
    public List<String> findFollowingAuthorIds(String accountId) {
        return follows.following(accountId);
    }
}
