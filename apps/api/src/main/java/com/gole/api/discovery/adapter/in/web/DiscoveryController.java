package com.gole.api.discovery.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.discovery.adapter.in.web.DiscoveryDtos.FollowRequest;
import com.gole.api.discovery.adapter.in.web.DiscoveryDtos.ListingSummaryResponse;
import com.gole.api.discovery.adapter.in.web.DiscoveryDtos.WishlistEntryResponse;
import com.gole.api.discovery.adapter.in.web.DiscoveryDtos.WishlistRequest;
import com.gole.api.discovery.application.port.in.FollowSellerUseCase;
import com.gole.api.discovery.application.port.in.GetPersonalizedFeedUseCase;
import com.gole.api.discovery.application.port.in.GetSellerShopUseCase;
import com.gole.api.discovery.application.port.in.ManageWishlistUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
@Tag(name = "Discovery", description = "셀러 샵·팔로우·위시리스트·피드")
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
    public void follow(
            @PathVariable String userId, @Valid @RequestBody FollowRequest request, HttpServletRequest http) {
        followSellerUseCase.follow(AuthenticatedUser.id(http), request.sellerId());
    }

    @DeleteMapping("/users/{userId}/following/{sellerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@PathVariable String userId, @PathVariable String sellerId, HttpServletRequest http) {
        followSellerUseCase.unfollow(AuthenticatedUser.id(http), sellerId);
    }

    @GetMapping("/users/{userId}/following")
    public List<String> following(@PathVariable String userId, HttpServletRequest http) {
        return followSellerUseCase.following(AuthenticatedUser.id(http));
    }

    @GetMapping("/users/{userId}/feed")
    public List<ListingSummaryResponse> feed(
            @PathVariable String userId, @RequestParam(defaultValue = "24") int limit, HttpServletRequest http) {
        return getPersonalizedFeedUseCase.feed(AuthenticatedUser.id(http), limit).stream()
                .map(ListingSummaryResponse::from)
                .toList();
    }

    @PostMapping("/users/{userId}/wishlist")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addWishlist(
            @PathVariable String userId, @Valid @RequestBody WishlistRequest request, HttpServletRequest http) {
        manageWishlistUseCase.add(AuthenticatedUser.id(http), request.targetType(), request.targetId());
    }

    @DeleteMapping("/users/{userId}/wishlist")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeWishlist(
            @PathVariable String userId,
            @RequestParam("targetType") com.gole.api.discovery.domain.model.WishlistTargetType targetType,
            @RequestParam("targetId") String targetId,
            HttpServletRequest http) {
        manageWishlistUseCase.remove(AuthenticatedUser.id(http), targetType, targetId);
    }

    @GetMapping("/users/{userId}/wishlist")
    public List<WishlistEntryResponse> wishlist(@PathVariable String userId, HttpServletRequest http) {
        return manageWishlistUseCase.list(AuthenticatedUser.id(http)).stream()
                .map(WishlistEntryResponse::from)
                .toList();
    }
}
