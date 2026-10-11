package com.gole.api.chat.adapter.out.listing;

import com.gole.api.chat.application.port.out.DirectTradeListingPort;
import com.gole.api.listing.application.port.in.MarkListingSoldUseCase;
import org.springframework.stereotype.Component;

/** 매물 컨텍스트 통합 어댑터. 직거래 완료를 listing 의 판매 완료 유스케이스로 넘긴다. */
@Component
public class ListingDirectTradeAdapter implements DirectTradeListingPort {

    private final MarkListingSoldUseCase markListingSold;

    public ListingDirectTradeAdapter(MarkListingSoldUseCase markListingSold) {
        this.markListingSold = markListingSold;
    }

    @Override
    public boolean markSoldIfActive(String listingId) {
        return markListingSold.markDirectTradeSoldIfActive(listingId);
    }
}
