package com.gole.api.discovery.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class FollowPersistenceAdapterTest {

    @Test
    void mapsSellerFollowersToRecipientIds() {
        FollowMongoRepository repository = mock(FollowMongoRepository.class);
        when(repository.findBySellerId("seller-1"))
                .thenReturn(List.of(
                        new FollowDocument("f1", "user-1", "seller-1"),
                        new FollowDocument("f2", "user-2", "seller-1")));

        FollowPersistenceAdapter adapter = new FollowPersistenceAdapter(repository);

        assertThat(adapter.findUserIdsBySeller("seller-1")).containsExactly("user-1", "user-2");
    }
}
