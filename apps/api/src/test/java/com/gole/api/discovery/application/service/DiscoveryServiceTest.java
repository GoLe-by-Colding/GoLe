package com.gole.api.discovery.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.discovery.application.port.out.FollowRepositoryPort;
import com.gole.api.discovery.application.port.out.ListingQueryPort;
import com.gole.api.discovery.application.port.out.WishlistRepositoryPort;
import com.gole.api.discovery.domain.exception.DuplicateFollowException;
import com.gole.api.discovery.domain.exception.DuplicateWishlistException;
import com.gole.api.discovery.domain.model.Follow;
import com.gole.api.discovery.domain.model.WishlistEntry;
import com.gole.api.discovery.domain.model.WishlistTargetType;
import com.gole.api.listing.domain.model.Listing;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscoveryServiceTest {

    private InMemoryFollows follows;
    private InMemoryWishlist wishlist;
    private DiscoveryService service;

    @BeforeEach
    void setUp() {
        follows = new InMemoryFollows();
        wishlist = new InMemoryWishlist();
        service = new DiscoveryService(follows, wishlist, new NoopListingQuery(), (sellerId, followerId) -> {});
    }

    @Test
    void follow_thenDuplicate_isRejected() {
        service.follow("u1", "s1");
        assertThat(service.following("u1")).containsExactly("s1");
        assertThatThrownBy(() -> service.follow("u1", "s1")).isInstanceOf(DuplicateFollowException.class);
    }

    @Test
    void follow_rejectsSelfFollow() {
        assertThatThrownBy(() -> service.follow("same-user", "same-user"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("자기 자신");
        assertThat(service.following("same-user")).isEmpty();
    }

    @Test
    void unfollow_isIdempotent() {
        service.follow("u1", "s1");
        service.unfollow("u1", "s1");
        service.unfollow("u1", "s1"); // 두 번째도 예외 없음
        assertThat(service.following("u1")).isEmpty();
    }

    @Test
    void feed_isEmptyWithoutFollowingAndBoundsRequestedLimit() {
        RecordingListingQuery listings = new RecordingListingQuery();
        service = new DiscoveryService(follows, wishlist, listings, (sellerId, followerId) -> {});

        assertThat(service.feed("u1", 24)).isEmpty();
        assertThat(listings.requestedSellerIds).isEmpty();

        service.follow("u1", "s1");
        service.follow("u1", "s2");
        service.feed("u1", 1_000);

        assertThat(listings.requestedSellerIds).containsExactly("s1", "s2");
        assertThat(listings.requestedLimit).isEqualTo(100);
    }

    @Test
    void wishlist_add_dup_remove() {
        service.add("u1", WishlistTargetType.LISTING, "l1");
        assertThatThrownBy(() -> service.add("u1", WishlistTargetType.LISTING, "l1"))
                .isInstanceOf(DuplicateWishlistException.class);
        service.remove("u1", WishlistTargetType.LISTING, "l1");
        assertThat(service.list("u1")).isEmpty();
    }

    private static final class InMemoryFollows implements FollowRepositoryPort {
        private final List<Follow> store = new ArrayList<>();

        @Override
        public boolean exists(String userId, String sellerId) {
            return store.stream()
                    .anyMatch(f -> f.userId().equals(userId) && f.sellerId().equals(sellerId));
        }

        @Override
        public void save(Follow follow) {
            store.add(follow);
        }

        @Override
        public void delete(String userId, String sellerId) {
            store.removeIf(f -> f.userId().equals(userId) && f.sellerId().equals(sellerId));
        }

        @Override
        public List<String> findSellerIdsByUser(String userId) {
            return store.stream()
                    .filter(f -> f.userId().equals(userId))
                    .map(Follow::sellerId)
                    .toList();
        }

        @Override
        public List<String> findUserIdsBySeller(String sellerId) {
            return store.stream()
                    .filter(f -> f.sellerId().equals(sellerId))
                    .map(Follow::userId)
                    .toList();
        }
    }

    private static final class InMemoryWishlist implements WishlistRepositoryPort {
        private final List<WishlistEntry> store = new ArrayList<>();

        @Override
        public boolean exists(String userId, WishlistTargetType type, String targetId) {
            return store.stream()
                    .anyMatch(e -> e.userId().equals(userId)
                            && e.targetType() == type
                            && e.targetId().equals(targetId));
        }

        @Override
        public void save(WishlistEntry entry) {
            store.add(entry);
        }

        @Override
        public void delete(String userId, WishlistTargetType type, String targetId) {
            store.removeIf(e -> e.userId().equals(userId)
                    && e.targetType() == type
                    && e.targetId().equals(targetId));
        }

        @Override
        public List<WishlistEntry> findByUser(String userId) {
            return store.stream().filter(e -> e.userId().equals(userId)).toList();
        }
    }

    private static final class NoopListingQuery implements ListingQueryPort {
        @Override
        public List<Listing> activeBySeller(String sellerId) {
            return List.of();
        }

        @Override
        public List<Listing> activeBySellers(List<String> sellerIds, int limit) {
            return List.of();
        }
    }

    private static final class RecordingListingQuery implements ListingQueryPort {
        private List<String> requestedSellerIds = List.of();
        private int requestedLimit;

        @Override
        public List<Listing> activeBySeller(String sellerId) {
            return List.of();
        }

        @Override
        public List<Listing> activeBySellers(List<String> sellerIds, int limit) {
            requestedSellerIds = List.copyOf(sellerIds);
            requestedLimit = limit;
            return List.of();
        }
    }
}
