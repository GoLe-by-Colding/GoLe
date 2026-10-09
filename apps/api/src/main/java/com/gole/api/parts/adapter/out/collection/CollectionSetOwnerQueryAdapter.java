package com.gole.api.parts.adapter.out.collection;

import com.gole.api.collection.application.port.in.ListSetHoldersUseCase;
import com.gole.api.collection.domain.model.OwnershipStatus;
import com.gole.api.parts.application.port.out.SetOwnerQueryPort;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 컬렉션 컨텍스트 통합 어댑터. 세트 보유자 조회를 collection의 인바운드 유스케이스로 위임한다.
 * 부품을 내줄 수 있는 사람은 그 세트를 실제로 가진 사람뿐이라 {@code OWNED}만 본다. (W8)
 */
@Component
public class CollectionSetOwnerQueryAdapter implements SetOwnerQueryPort {

    private final ListSetHoldersUseCase holders;

    public CollectionSetOwnerQueryAdapter(ListSetHoldersUseCase holders) {
        this.holders = holders;
    }

    @Override
    public List<String> ownersOf(String setNumber) {
        return holders.holdersOf(setNumber, OwnershipStatus.OWNED);
    }
}
