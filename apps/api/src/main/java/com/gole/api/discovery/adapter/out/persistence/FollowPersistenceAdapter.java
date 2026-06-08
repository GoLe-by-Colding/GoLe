package com.gole.api.discovery.adapter.out.persistence;

import com.gole.api.discovery.application.port.out.FollowRepositoryPort;
import com.gole.api.discovery.domain.model.Follow;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 팔로우 영속성 어댑터. 도메인 {@link Follow}와 {@link FollowDocument}를 매핑한다.
 */
@Component
public class FollowPersistenceAdapter implements FollowRepositoryPort {

    private final FollowMongoRepository repository;

    public FollowPersistenceAdapter(FollowMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean exists(String userId, String sellerId) {
        return repository.existsByUserIdAndSellerId(userId, sellerId);
    }

    @Override
    public void save(Follow follow) {
        repository.save(toDocument(follow));
    }

    @Override
    public void delete(String userId, String sellerId) {
        repository.deleteByUserIdAndSellerId(userId, sellerId);
    }

    @Override
    public List<String> findSellerIdsByUser(String userId) {
        return repository.findByUserId(userId).stream()
                .map(FollowDocument::getSellerId)
                .toList();
    }

    private FollowDocument toDocument(Follow follow) {
        // id 는 MongoDB가 생성하도록 null 로 둔다.
        return new FollowDocument(null, follow.userId(), follow.sellerId());
    }
}
