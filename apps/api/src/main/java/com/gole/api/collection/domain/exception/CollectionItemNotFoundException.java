package com.gole.api.collection.domain.exception;

import com.gole.api.common.exception.NotFoundException;

public class CollectionItemNotFoundException extends NotFoundException {

    public CollectionItemNotFoundException(String itemId) {
        super("COLLECTION_ITEM_NOT_FOUND", "Collection item not found: " + itemId);
    }
}
