import { Badge, Card, MediaImage } from "@shared/ui";
import { thumbnailUrl } from "@shared/lib";
import type { Listing } from "@gole/core/listing";
import {
  completenessLabel,
  conditionLabel,
  formatPriceKrw,
  LISTING_CATEGORY_LABEL,
  priceDropAmount,
} from "@gole/core/listing";

export interface ListingCardProps {
  readonly listing: Listing;
  /** 같은 등급 추정 시세와의 차이 한 줄(예: "추정 시세보다 5.3% 낮음"). 계산은 상위 레이어가 한다. */
  readonly priceNote?: string | undefined;
}

/**
 * 탐색 목록 카드. 모바일 2열(카드 폭 약 150px)에서도 비교가 되도록 위계를 고정한다 —
 * 세트 번호(식별) → 제목(2줄) → 가격(가장 굵게, 줄바꿈 금지) → 상태·고지 배지.
 * 상태 등급·구성·부품 누락·예약중은 구매 판단에 필요한 고지라 폭이 좁아도 숨기지 않고 줄을 바꾼다.
 */
export function ListingCard({ listing, priceNote }: ListingCardProps) {
  const cover = listing.photoUrls[0];

  return (
    <Card interactive padded={false} className="flex h-full flex-col" data-testid="listing-card">
      <div className="overflow-hidden">
        <MediaImage
          className="aspect-[4/3] w-full bg-neutral-100 object-cover"
          src={cover === undefined ? null : thumbnailUrl(cover, 480)}
          alt={listing.title}
          loading="lazy"
          fallback="이미지 준비 중"
        />
      </div>
      <div className="flex flex-1 flex-col gap-1.5 p-3 sm:gap-2 sm:p-4">
        {listing.catalogSetNumber !== null ? (
          <span className="text-[11px] font-medium tabular-nums text-neutral-400 sm:text-xs">
            #{listing.catalogSetNumber}
          </span>
        ) : null}
        <span className="line-clamp-2 text-sm font-semibold leading-snug text-neutral-900 sm:text-[15px]">
          {listing.title}
        </span>
        <div className="flex flex-wrap items-baseline gap-x-1.5 gap-y-0.5">
          <span className="whitespace-nowrap text-base font-extrabold tracking-tight tabular-nums text-neutral-900 sm:text-xl">
            {formatPriceKrw(listing.price)}
          </span>
          {priceDropAmount(listing) !== null ? (
            <span className="whitespace-nowrap text-xs font-semibold text-success">가격 내림</span>
          ) : null}
        </div>
        {priceNote === undefined ? null : (
          <span className="text-[11px] leading-snug break-keep text-neutral-500 sm:text-xs">
            {priceNote}
          </span>
        )}
        <div className="mt-auto flex flex-wrap gap-1 pt-1">
          {listing.status === "reserved" ? <Badge tone="warning">예약중</Badge> : null}
          {listing.category !== "set" ? (
            <Badge tone="brand">{LISTING_CATEGORY_LABEL[listing.category]}</Badge>
          ) : null}
          <Badge tone="neutral">{conditionLabel(listing.condition)}</Badge>
          <Badge tone="brand">{completenessLabel(listing.completeness)}</Badge>
          {listing.hasMissingParts ? <Badge tone="warning">부품 누락</Badge> : null}
        </div>
      </div>
    </Card>
  );
}
