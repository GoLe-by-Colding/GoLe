package com.gole.api.collection.application.port.out;

import com.gole.api.collection.domain.model.CollectionItem;
import java.util.List;
import java.util.Optional;

/**
 * 컬렉션 영속성 outbound port. 도메인/애플리케이션은 저장 기술(MongoDB 등)에 의존하지 않는다.
 */
public interface CollectionRepositoryPort {

    /** 컬렉션 항목을 저장(신규/갱신)하고 영속된 결과를 반환한다. */
    CollectionItem save(CollectionItem item);

    /** id로 단건 조회. 없으면 비어있음. */
    Optional<CollectionItem> findById(String itemId);

    /** 특정 사용자의 컬렉션 항목 전체를 조회한다. */
    List<CollectionItem> findByUser(String userId);

    /** 컬렉션 항목을 삭제한다. */
    void delete(CollectionItem item);
}
