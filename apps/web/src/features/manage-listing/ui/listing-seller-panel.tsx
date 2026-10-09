"use client";

import type { ReactNode } from "react";
import type { Listing } from "@entities/listing";
import { BumpListingButton } from "./bump-listing-button";
import { EditListingLink } from "./edit-listing-link";

export interface ListingSellerPanelProps {
  readonly listing: Listing;
  /**
   * 매물 관리 아래에 이어 붙일 판매자 전용 구획(예: 받은 가격 제안, 최고 입찰가에 바로 판매).
   * 각 구획은 자기 제목을 가진 `<section>`으로 넘긴다.
   */
  readonly children?: ReactNode;
}

/**
 * 매물 상세의 판매자 전용 패널. 본인 매물인지 판정은 호출부가 한다(세션 비교).
 *
 * <p>수정·끌올은 판매 중(`active`)일 때만 열린다. 예약·판매완료 매물은 서버도 409로 거부하므로
 * 버튼 대신 이유를 보여 준다.
 */
export function ListingSellerPanel({ listing, children }: ListingSellerPanelProps) {
  const manageable = listing.status === "active";

  return (
    <div
      className="flex flex-col divide-y divide-neutral-200 rounded-lg border border-neutral-200 bg-white"
      data-testid="listing-seller-panel"
    >
      <section aria-labelledby="listing-manage-heading" className="flex flex-col gap-3 p-4">
        <div className="flex flex-col gap-0.5">
          <h2 id="listing-manage-heading" className="text-sm font-semibold text-neutral-900">
            내 매물 관리
          </h2>
          <p className="text-xs leading-relaxed text-neutral-500">
            {manageable
              ? "가격을 내리면 이 매물을 찜한 분들께 알림이 가요. 끌올하면 최신순 맨 앞으로 올라가요."
              : listing.status === "reserved"
                ? "거래가 진행 중이라 지금은 수정하거나 끌올할 수 없어요."
                : "판매가 끝난 매물은 수정하거나 끌올할 수 없어요."}
          </p>
        </div>
        {manageable ? (
          <div className="flex flex-wrap items-start gap-2">
            <EditListingLink listingId={listing.id} />
            <BumpListingButton listing={listing} />
          </div>
        ) : null}
      </section>
      {children}
    </div>
  );
}
