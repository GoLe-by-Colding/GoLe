package com.gole.api.parts.adapter.out.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.collection.application.port.in.ListSetHoldersUseCase;
import com.gole.api.collection.domain.model.OwnershipStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CollectionSetOwnerQueryAdapterTest {

    @Test
    @DisplayName("부품을 내줄 수 있는 실제 보유자(OWNED)만 묻는다")
    void ownersOf_asksOnlyOwnedHolders() {
        ListSetHoldersUseCase holders = (setNumber, status) -> switch (status) {
            case OWNED -> List.of("owner-" + setNumber);
            case WANTED -> List.of("wanted");
            case SOLD -> List.of("sold");
        };

        assertThat(new CollectionSetOwnerQueryAdapter(holders).ownersOf("10305"))
                .containsExactly("owner-10305");
        assertThat(OwnershipStatus.values()).contains(OwnershipStatus.OWNED);
    }
}
