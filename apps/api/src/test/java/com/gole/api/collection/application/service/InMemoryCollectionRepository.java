package com.gole.api.collection.application.service;

import com.gole.api.collection.application.port.out.CollectionRepositoryPort;
import com.gole.api.collection.domain.model.CollectionItem;
import com.gole.api.collection.domain.model.OwnershipStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 컬렉션 테스트용 가짜 저장소. {@link #failFindByUser}로 특정 사용자 조회 장애를 흉내 낸다. */
class InMemoryCollectionRepository implements CollectionRepositoryPort {

    final List<CollectionItem> store = new ArrayList<>();
    final List<String> cursors = new ArrayList<>();
    private final Set<String> failingUsers = new HashSet<>();

    void failFindByUser(String userId) {
        failingUsers.add(userId);
    }

    @Override
    public CollectionItem save(CollectionItem item) {
        store.add(item);
        return item;
    }

    @Override
    public Optional<CollectionItem> findById(String itemId) {
        return store.stream().filter(i -> i.id().equals(itemId)).findFirst();
    }

    @Override
    public List<CollectionItem> findByUser(String userId) {
        if (failingUsers.contains(userId)) {
            throw new IllegalStateException("collection lookup failed for " + userId);
        }
        return store.stream().filter(i -> i.userId().equals(userId)).toList();
    }

    @Override
    public List<String> findUserIdsBySetAndStatus(String setNumber, OwnershipStatus status) {
        return store.stream()
                .filter(i -> i.setNumber().equals(setNumber) && i.status() == status)
                .map(CollectionItem::userId)
                .toList();
    }

    @Override
    public List<String> findUserIdsWithStatus(OwnershipStatus status, String afterUserId, int limit) {
        cursors.add(afterUserId);
        return store.stream()
                .filter(i -> i.status() == status)
                .map(CollectionItem::userId)
                .filter(id -> afterUserId == null || id.compareTo(afterUserId) > 0)
                .distinct()
                .sorted()
                .limit(limit)
                .toList();
    }

    @Override
    public void delete(CollectionItem item) {
        store.removeIf(i -> i.id().equals(item.id()));
    }
}
