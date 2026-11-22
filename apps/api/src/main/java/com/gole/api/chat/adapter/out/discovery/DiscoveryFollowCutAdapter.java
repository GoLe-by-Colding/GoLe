package com.gole.api.chat.adapter.out.discovery;

import com.gole.api.chat.application.port.out.FollowCutPort;
import com.gole.api.discovery.application.port.in.FollowSellerUseCase;
import org.springframework.stereotype.Component;

/** discovery 언팔로우 유스케이스를 chat 차단의 팔로우 해제 포트에 연결한다. */
@Component
public class DiscoveryFollowCutAdapter implements FollowCutPort {

    private final FollowSellerUseCase follows;

    public DiscoveryFollowCutAdapter(FollowSellerUseCase follows) {
        this.follows = follows;
    }

    @Override
    public void cutBoth(String a, String b) {
        follows.unfollow(a, b);
        follows.unfollow(b, a);
    }
}
