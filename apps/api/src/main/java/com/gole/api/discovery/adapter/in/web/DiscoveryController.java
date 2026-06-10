package com.gole.api.discovery.adapter.in.web;

import com.gole.api.discovery.adapter.in.web.DiscoveryDtos.FollowRequest;
import com.gole.api.discovery.adapter.in.web.DiscoveryDtos.ListingSummaryResponse;
import com.gole.api.discovery.adapter.in.web.DiscoveryDtos.WishlistEntryResponse;
import com.gole.api.discovery.adapter.in.web.DiscoveryDtos.WishlistRequest;
import com.gole.api.discovery.application.port.in.FollowSellerUseCase;
import com.gole.api.discovery.application.port.in.GetPersonalizedFeedUseCase;
import com.gole.api.discovery.application.port.in.GetSellerShopUseCase;
import com.gole.api.discovery.application.port.in.ManageWishlistUseCase;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 셀러 샵/팔로우/위시리스트/개인화 피드. (요구사항 16, 17)
 */
@RestController
@RequestMapping("/api/v1")
public class DiscoveryController {

    private final FollowSellerUseCase followSellerUseCase;
    private final GetSellerShopUseCase getSellerShopUseCase;
    private final GetPersonalizedFeedUseCase getPersonalizedFeedUseCase;
    private final ManageWishlistUseCase manageWishlistUseCase;

    public DiscoveryController(
            FollowSellerUseCase followSellerUseCase,
            GetSellerShopUseCase getSellerShopUseCase,
            GetPersonalizedFeedUseCase getPersonalizedFeedUseCase,
            ManageWishlistUseCase manageWishlistUseCase) {
        this.followSellerUseCase = followSellerUseCase;
        this.getSellerShopUseCase = getSellerShopUseCase;
        this.getPersonalizedFeedUseCase = getPersonalizedFeedUseCase;
        this.manageWishlistUseCase = manageWishlistUseCase;
    }

    @GetMapping("/shops/{sellerId}")
    public List<ListingSummaryResponse> shop(@PathVariable String sellerId) {
        return getSellerShopUseCase.shopListings(sellerId).stream()
                .map(ListingSummaryResponse::from)
                .toList();
    }

    @PostMapping("/users/{userId}/following")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@PathVariable String userId, @Valid @RequestBody FollowRequest request) {
        followSellerUseCase.follow(userId, request.sellerId());
    }

    @DeleteMapping("/users/{userId}/following/{sellerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@PathVariable String userId, @PathVariable String sellerId) {
        followSellerUseCase.unfollow(userId, sellerId);
    }

    @GetMapping("/users/{userId}/following")
    public List<String> following(@PathVariable String userId) {
        return followSellerUseCase.following(userId);
    }

    @GetMapping("/users/{userId}/feed")
    public List<ListingSummaryResponse> feed(@PathVariable String userId) {
        return getPersonalizedFeedUseCase.feed(userId).stream()
                .map(ListingSummaryResponse::from)
                .toList();
    }

    @PostMapping("/users/{userId}/wishlist")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addWishlist(@PathVariable String userId, @Valid @RequestBody WishlistRequest request) {
        manageWishlistUseCase.add(userId, request.targetType(), request.targetId());
    }

    @DeleteMapping("/users/{userId}/wishlist")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeWishlist(
            @PathVariable String userId,
            @RequestParam("targetType") com.gole.api.discovery.domain.model.WishlistTargetType targetType,
            @RequestParam("targetId") String targetId) {
        manageWishlistUseCase.remove(userId, targetType, targetId);
    }

    @GetMapping("/users/{userId}/wishlist")
    public List<WishlistEntryResponse> wishlist(@PathVariable String userId) {
        return manageWishlistUseCase.list(userId).stream()
                .map(WishlistEntryResponse::from)
                .toList();
    }
}
