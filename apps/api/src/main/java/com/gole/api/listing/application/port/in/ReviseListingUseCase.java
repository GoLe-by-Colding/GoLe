package com.gole.api.listing.application.port.in;

import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.InterestTag;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import java.util.List;

/**
 * Inbound port: 판매자의 매물 수정. (listing-edit-and-bump E1~E9)
 *
 * <p>세트 번호·카테고리는 명령에 없다(E2). 소유자가 아니면 403, 판매 중(ACTIVE)이 아니면 409.
 */
public interface ReviseListingUseCase {

    RevisionResult revise(ReviseListingCommand command);

    /**
     * @param sellerId    요청자. 매물의 판매자와 같아야 한다.
     * @param photoKeys   수정 후 전체 사진 목록(저장 키). 빠진 기존 사진은 회수된다(E5).
     * @param interestTag 관심 테마. null이면 지정 해제. 바꿔도 알림톡을 다시 보내지 않는다(E7).
     */
    record ReviseListingCommand(
            String listingId,
            String sellerId,
            String title,
            String description,
            long price,
            ItemCondition condition,
            ConditionDisclosure disclosure,
            List<String> photoKeys,
            InterestTag interestTag) {}

    /**
     * @param listing      수정 후 매물
     * @param priceDropped 이번 수정으로 가격이 내려갔는지
     * @param oldPrice     수정 전 가격
     */
    record RevisionResult(Listing listing, boolean priceDropped, long oldPrice) {}
}
