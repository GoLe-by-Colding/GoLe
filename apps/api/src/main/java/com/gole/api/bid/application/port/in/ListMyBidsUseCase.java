package com.gole.api.bid.application.port.in;

import com.gole.api.bid.domain.model.Bid;
import java.util.List;

/** Inbound port: 내 입찰 목록(최신순, 유효 상태 반영). (buy-bids D5) */
public interface ListMyBidsUseCase {

    int MAX_ITEMS = 100;

    List<Bid> mine(String bidderId);
}
