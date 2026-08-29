package com.gole.api.discovery.application.service;

import com.gole.api.discovery.application.port.in.ListSellerFollowersUseCase;
import com.gole.api.discovery.application.port.out.FollowRepositoryPort;
import java.util.List;
import org.springframework.stereotype.Service;

/** 매물 컨텍스트가 순환 의존 없이 팔로워 수신자를 조회하도록 분리한 읽기 서비스. */
@Service
public class SellerFollowerQueryService implements ListSellerFollowersUseCase {

    private final FollowRepositoryPort followRepository;

    public SellerFollowerQueryService(FollowRepositoryPort followRepository) {
        this.followRepository = followRepository;
    }

    @Override
    public List<String> followersOf(String sellerId) {
        return followRepository.findUserIdsBySeller(sellerId);
    }
}
