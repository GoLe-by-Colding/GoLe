package com.gole.api.discovery.application.service;

import com.gole.api.discovery.application.port.in.ListListingWishersUseCase;
import com.gole.api.discovery.application.port.out.WishlistRepositoryPort;
import com.gole.api.discovery.domain.model.WishlistTargetType;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * listing이 순환 의존 없이 찜한 사용자를 조회하도록 분리한 읽기 서비스.
 *
 * <p>{@link SetWisherQueryService}와 같은 이유로 저장소에만 의존한다. {@code DiscoveryService}는
 * listing의 인바운드 포트를 쓰므로, listing이 거기에 다시 기대면 빈 생성이 순환한다.
 */
@Service
public class ListingWisherQueryService implements ListListingWishersUseCase {

    private final WishlistRepositoryPort wishlistRepository;

    public ListingWisherQueryService(WishlistRepositoryPort wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    @Override
    public List<String> wishersOf(String listingId) {
        return wishlistRepository.findUserIdsByTarget(WishlistTargetType.LISTING, listingId);
    }
}
