package com.gole.api.listing.application.port.in;

import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.ListingCategory;
import java.util.List;

/**
 * Inbound port: 리스팅 생성. (요구사항 5.1~5.5)
 */
public interface CreateListingUseCase {

    String create(CreateListingCommand command);

    record CreateListingCommand(
            String sellerId,
            String title,
            String description,
            long price,
            ItemCondition condition,
            ConditionDisclosure disclosure,
            List<String> photoUrls,
            String catalogSetNumber,
            ListingCategory category) {

        /** 카테고리 미지정(레거시) — 세트로 간주. */
        public CreateListingCommand(
                String sellerId,
                String title,
                String description,
                long price,
                ItemCondition condition,
                ConditionDisclosure disclosure,
                List<String> photoUrls,
                String catalogSetNumber) {
            this(sellerId, title, description, price, condition, disclosure,
                    photoUrls, catalogSetNumber, ListingCategory.SET);
        }
    }
}
