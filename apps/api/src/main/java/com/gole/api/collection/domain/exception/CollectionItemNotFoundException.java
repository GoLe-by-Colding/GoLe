package com.gole.api.collection.domain.exception;

import com.gole.api.common.exception.NotFoundException;

public class CollectionItemNotFoundException extends NotFoundException {

    public CollectionItemNotFoundException(String itemId) {
        super("COLLECTION_ITEM_NOT_FOUND", "컬렉션 항목을 찾을 수 없습니다");
    }
}
