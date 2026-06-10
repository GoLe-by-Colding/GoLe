package com.gole.api.collection.application.port.in;

import com.gole.api.collection.domain.model.CollectionItem;
import com.gole.api.collection.domain.model.OwnershipStatus;
import java.util.List;

/**
 * Inbound port: 컬렉션 추가/조회/삭제. (요구사항 11.1, 11.3, 11.4)
 */
public interface ManageCollectionUseCase {

    String add(AddCommand command);

    List<CollectionItem> getCollection(String userId);

    void remove(String itemId, String userId);

    record AddCommand(String userId, String setNumber, OwnershipStatus status) {}
}
