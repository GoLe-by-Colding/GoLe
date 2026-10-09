package com.gole.api.collection.application.port.out;

import com.gole.api.collection.domain.model.CollectionValueSnapshot;
import java.time.LocalDate;
import java.util.List;

/** Outbound port: 컬렉션 가치 스냅샷 영속성. 사용자·날짜당 하나다. */
public interface CollectionValueSnapshotRepositoryPort {

    /** 같은 사용자·날짜가 있으면 덮어쓴다. */
    void upsert(CollectionValueSnapshot snapshot);

    /** {@code from}~{@code to}(양끝 포함) 스냅샷을 날짜 오름차순으로. */
    List<CollectionValueSnapshot> findRange(String userId, LocalDate from, LocalDate to);
}
