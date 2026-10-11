package com.gole.api.collection.application.port.in;

import com.gole.api.collection.domain.model.CollectionValueSnapshot;
import java.util.List;

/** Inbound port: 내 컬렉션 자산 추이 조회. (collection-value-history H3·H4) */
public interface GetCollectionValueHistoryUseCase {

    int DEFAULT_DAYS = 90;
    int MAX_DAYS = 365;

    /**
     * 오늘(Asia/Seoul)을 포함한 최근 {@code days}일의 스냅샷을 날짜 오름차순으로 돌려준다. 오늘 스냅샷은 먼저 갱신한다.
     *
     * @throws com.gole.api.common.exception.BadRequestException {@code days}가 1~365 밖일 때({@code INVALID_PARAMETER})
     */
    List<CollectionValueSnapshot> history(String userId, int days);
}
