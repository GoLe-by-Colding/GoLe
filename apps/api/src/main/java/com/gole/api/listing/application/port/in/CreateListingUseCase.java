package com.gole.api.listing.application.port.in;

import com.gole.api.listing.domain.model.ItemCondition;
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
            List<String> photoUrls,
            String catalogSetNumber) {
    }
}
