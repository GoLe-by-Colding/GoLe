package com.gole.api.listing.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 판매자가 수정할 수 있는 매물 필드 묶음. 통째로 교체한다. (listing-edit-and-bump E1)
 *
 * <p>세트 번호·카테고리는 여기 없다(E2). 세트가 바뀌면 관심 세트 알림·입찰 매칭·시세 귀속이
 * 모두 틀어지므로 수정 대상에서 타입으로 뺀다.
 *
 * @param interestTag 관심 테마. null이면 지정 해제.
 */
public record ListingRevision(
        String title,
        String description,
        Money price,
        ItemCondition condition,
        ConditionDisclosure disclosure,
        List<String> photoKeys,
        InterestTag interestTag) {

    public ListingRevision {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(condition, "condition");
        disclosure = disclosure == null ? ConditionDisclosure.basic() : disclosure;
        photoKeys = photoKeys == null ? List.of() : List.copyOf(photoKeys);
    }
}
