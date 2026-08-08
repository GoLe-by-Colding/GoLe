package com.gole.api.discovery.application.service;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.discovery.application.port.in.FollowSellerUseCase;
import com.gole.api.discovery.application.port.in.GetPersonalizedFeedUseCase;
import com.gole.api.discovery.application.port.in.GetSellerShopUseCase;
import com.gole.api.discovery.application.port.in.ManageWishlistUseCase;
import com.gole.api.discovery.application.port.out.FollowNotifierPort;
import com.gole.api.discovery.application.port.out.FollowRepositoryPort;
import com.gole.api.discovery.application.port.out.ListingQueryPort;
import com.gole.api.discovery.application.port.out.WishlistRepositoryPort;
import com.gole.api.discovery.domain.exception.DuplicateFollowException;
import com.gole.api.discovery.domain.exception.DuplicateWishlistException;
import com.gole.api.discovery.domain.model.Follow;
import com.gole.api.discovery.domain.model.WishlistEntry;
import com.gole.api.discovery.domain.model.WishlistTargetType;
import com.gole.api.listing.domain.model.Listing;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 디스커버리 유스케이스: 셀러 샵/팔로우/위시리스트/개인화 피드. (요구사항 16, 17)
 */
@Service
public class DiscoveryService
        implements FollowSellerUseCase, GetSellerShopUseCase, GetPersonalizedFeedUseCase, ManageWishlistUseCase {

    private final FollowRepositoryPort followRepository;
    private final WishlistRepositoryPort wishlistRepository;
    private final ListingQueryPort listingQuery;
    private final FollowNotifierPort followNotifier;

    public DiscoveryService(
            FollowRepositoryPort followRepository,
            WishlistRepositoryPort wishlistRepository,
            ListingQueryPort listingQuery,
            FollowNotifierPort followNotifier) {
        this.followRepository = followRepository;
        this.wishlistRepository = wishlistRepository;
        this.listingQuery = listingQuery;
        this.followNotifier = followNotifier;
    }

    @Override
    public void follow(String userId, String sellerId) {
        if (userId.equals(sellerId)) {
            throw new ForbiddenException("FOLLOW_SELF_NOT_ALLOWED", "자기 자신은 팔로우할 수 없습니다");
        }
        if (followRepository.exists(userId, sellerId)) {
            throw new DuplicateFollowException(); // 요구사항 16.4
        }
        followRepository.save(new Follow(userId, sellerId));
        // 알림: 새 팔로워를 셀러에게(best-effort)
        followNotifier.notifyNewFollower(sellerId, userId);
    }

    @Override
    public void unfollow(String userId, String sellerId) {
        followRepository.delete(userId, sellerId); // 요구사항 16.5 (멱등)
    }

    @Override
    public List<String> following(String userId) {
        return followRepository.findSellerIdsByUser(userId);
    }

    @Override
    public List<Listing> shopListings(String sellerId) {
        return listingQuery.activeBySeller(sellerId);
    }

    @Override
    public List<Listing> feed(String userId) {
        return listingQuery.activeBySellers(followRepository.findSellerIdsByUser(userId));
    }

    @Override
    public void add(String userId, WishlistTargetType type, String targetId) {
        if (wishlistRepository.exists(userId, type, targetId)) {
            throw new DuplicateWishlistException(); // 요구사항 17.3
        }
        wishlistRepository.save(new WishlistEntry(userId, type, targetId));
    }

    @Override
    public void remove(String userId, WishlistTargetType type, String targetId) {
        wishlistRepository.delete(userId, type, targetId); // 요구사항 17.4
    }

    @Override
    public List<WishlistEntry> list(String userId) {
        return wishlistRepository.findByUser(userId);
    }
}
