package com.gole.api.notification.application.port.out;

import java.util.Optional;

/** 발송 시점의 매물 존재·활성 상태를 notification 컨텍스트 타입으로 조회한다. */
public interface ListingSnapshotPort {

    Optional<ListingSnapshot> findById(String listingId);

    record ListingSnapshot(String listingId, boolean active) {}
}
