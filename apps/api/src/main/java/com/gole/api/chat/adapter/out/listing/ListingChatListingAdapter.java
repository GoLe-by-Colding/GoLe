package com.gole.api.chat.adapter.out.listing;

import com.gole.api.chat.application.port.out.ChatListingPort;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import org.springframework.stereotype.Component;

/** 매물 컨텍스트 통합 어댑터. 매물 도메인 객체에서 채팅에 필요한 판매자만 꺼낸다. */
@Component
public class ListingChatListingAdapter implements ChatListingPort {

    private final GetListingUseCase listings;

    public ListingChatListingAdapter(GetListingUseCase listings) {
        this.listings = listings;
    }

    @Override
    public String sellerOf(String listingId) {
        return listings.getById(listingId).getSellerId();
    }

    @Override
    public void requirePublic(String listingId) {
        listings.getPublicById(listingId);
    }
}
