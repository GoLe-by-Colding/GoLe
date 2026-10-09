"use client";

import { useState } from "react";
import type { Listing } from "@entities/listing";
import { ReceivedOffersSection } from "./received-offers-section";
import { SellToBidSection } from "./sell-to-bid-section";

export interface SellerOfferSectionsProps {
  /** 입찰가에 맞춰 판매가를 올려 팔 때 수정 본문을 다시 만들어야 해서 매물 전체를 받는다. */
  readonly listing: Listing;
  readonly paymentsOpen: boolean;
}

/**
 * 판매자 패널 아래에 붙는 "받은 가격 제안"과 "구매 입찰" 구획. `ListingSellerPanel`의 `children`으로 넘긴다.
 *
 * 둘을 한 컴포넌트로 묶는 이유: 최고 입찰가에 팔면 입찰자에게 수락 제안이 생기므로(buy-bids D7),
 * 판매 직후 받은 제안 목록을 다시 읽어 그 제안이 바로 보이게 한다.
 */
export function SellerOfferSections({ listing, paymentsOpen }: SellerOfferSectionsProps) {
  const [filledCount, setFilledCount] = useState(0);
  return (
    <>
      <ReceivedOffersSection
        listing={listing}
        paymentsOpen={paymentsOpen}
        refreshKey={filledCount}
      />
      <SellToBidSection
        listing={listing}
        onFilled={() => setFilledCount((current) => current + 1)}
      />
    </>
  );
}
