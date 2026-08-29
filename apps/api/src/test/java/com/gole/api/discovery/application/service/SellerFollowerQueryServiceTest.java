package com.gole.api.discovery.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.discovery.application.port.out.FollowRepositoryPort;
import java.util.List;
import org.junit.jupiter.api.Test;

class SellerFollowerQueryServiceTest {

    @Test
    void delegatesRecipientLookupWithoutDependingOnListingQueries() {
        FollowRepositoryPort repository = mock(FollowRepositoryPort.class);
        when(repository.findUserIdsBySeller("seller-1")).thenReturn(List.of("user-1", "user-2"));

        SellerFollowerQueryService service = new SellerFollowerQueryService(repository);

        assertThat(service.followersOf("seller-1")).containsExactly("user-1", "user-2");
    }
}
