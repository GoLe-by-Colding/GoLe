package com.gole.api.discovery.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * 셀러 샵·팔로잉 피드에 보이는 매물 요약. 매물 컨텍스트의 매물 모델을 디스커버리가 보여 줄 값으로 환원한 것이다.
 *
 * @param condition 상태 등급 이름(예: {@code NEW_SEALED})
 * @param category 분류 이름(예: {@code SET})
 * @param status 매물 상태 이름(예: {@code ACTIVE})
 * @param photoUrls 저장된 사진 값(공개 경로 변환은 웹 경계가 한다)
 * @param listedAt 정렬 키(등록 또는 마지막 끌올)
 * @param previousPrice 지금 가격이 직전보다 쌀 때만 있는 직전가
 */
public record DiscoveredListing(
        String id,
        String sellerId,
        String title,
        long price,
        String condition,
        String catalogSetNumber,
        String category,
        String status,
        List<String> photoUrls,
        Instant createdAt,
        Instant listedAt,
        Long previousPrice) {

    public DiscoveredListing {
        photoUrls = photoUrls == null ? List.of() : List.copyOf(photoUrls);
    }
}
