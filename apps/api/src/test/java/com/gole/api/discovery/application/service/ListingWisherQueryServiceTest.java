package com.gole.api.discovery.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.discovery.application.port.out.WishlistRepositoryPort;
import com.gole.api.discovery.domain.model.WishlistTargetType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListingWisherQueryServiceTest {

    @Test
    void wishersOfReadsListingTargetsOnly() {
        WishlistRepositoryPort repository = mock(WishlistRepositoryPort.class);
        when(repository.findUserIdsByTarget(WishlistTargetType.LISTING, "listing-1"))
                .thenReturn(List.of("user-1", "user-2"));
        // 같은 id 문자열이라도 세트 찜은 매물 찜이 아니다.
        when(repository.findUserIdsByTarget(WishlistTargetType.CATALOG_SET, "listing-1"))
                .thenReturn(List.of("set-wisher"));

        assertThat(new ListingWisherQueryService(repository).wishersOf("listing-1"))
                .containsExactly("user-1", "user-2");
    }
}
