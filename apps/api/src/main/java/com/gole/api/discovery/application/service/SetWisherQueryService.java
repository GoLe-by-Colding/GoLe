package com.gole.api.discovery.application.service;

import com.gole.api.discovery.application.port.in.ListSetWishersUseCase;
import com.gole.api.discovery.application.port.out.WishlistRepositoryPort;
import com.gole.api.discovery.domain.model.WishlistTargetType;
import java.util.List;
import org.springframework.stereotype.Service;

/** catalog·listing이 순환 의존 없이 관심 세트 수신자를 조회하도록 분리한 읽기 서비스. */
@Service
public class SetWisherQueryService implements ListSetWishersUseCase {

    private final WishlistRepositoryPort wishlistRepository;

    public SetWisherQueryService(WishlistRepositoryPort wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    @Override
    public List<String> wishersOf(String setNumber) {
        return wishlistRepository.findUserIdsByTarget(WishlistTargetType.CATALOG_SET, setNumber);
    }
}
