"use client";

import { useRef, useState, useEffect, type ReactNode } from "react";
import Link from "next/link";
import { ListingCard, type Listing } from "@entities/listing";
import { Button, Text } from "@shared/ui";

export interface ListingGridProps {
  readonly listings: readonly Listing[];
  readonly emptyMessage?: string;
  readonly emptyAction?: ReactNode;
  /** 초기 노출 수. 기본 20. */
  readonly pageSize?: number;
  /** 매물 id → 같은 등급 추정 시세와의 차이 한 줄. 서버에서 `buildPriceNotes`로 만든다. */
  readonly priceNotes?: Readonly<Record<string, string>> | undefined;
}

/**
 * 클라이언트 측 점진적 로드(더 보기). 백엔드 페이지네이션 없이 브라우저에서 슬라이싱한다.
 * IntersectionObserver로 스크롤 말단 감지 시 자동으로 다음 배치를 노출한다.
 */
export function ListingGrid({
  listings,
  emptyMessage = "표시할 상품이 없습니다.",
  emptyAction,
  pageSize = 20,
  priceNotes,
}: ListingGridProps) {
  const [visible, setVisible] = useState(pageSize);
  const sentinelRef = useRef<HTMLDivElement>(null);

  // 스크롤 말단 감지 → 자동 로드
  useEffect(() => {
    const el = sentinelRef.current;
    if (!el || visible >= listings.length) return;
    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          setVisible((v) => Math.min(v + pageSize, listings.length));
        }
      },
      { rootMargin: "200px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [listings.length, visible, pageSize]);

  if (listings.length === 0) {
    return (
      <div className="flex flex-col items-center gap-4 p-12 text-center">
        <Text tone="muted">{emptyMessage}</Text>
        {emptyAction === undefined ? null : emptyAction}
      </div>
    );
  }

  const shown = listings.slice(0, visible);
  const remaining = listings.length - visible;

  return (
    <div>
      {/* 360px 이상 휴대폰은 2열로 나란히 비교한다(390px에서 한 화면에 카드 1장뿐이던 1열을 바꿈).
          320px급 좁은 폭은 카드가 130px 아래로 줄어 긴 금액이 넘치므로 1열을 유지한다. */}
      <div
        className="grid grid-cols-1 gap-3 min-[360px]:grid-cols-2 sm:[grid-template-columns:repeat(auto-fill,minmax(200px,1fr))] sm:gap-5"
        data-testid="listing-grid"
      >
        {shown.map((listing) => (
          <Link key={listing.id} href={`/listings/${listing.id}`} className="block h-full">
            <ListingCard listing={listing} priceNote={priceNotes?.[listing.id]} />
          </Link>
        ))}
      </div>

      {remaining > 0 ? (
        <>
          <div ref={sentinelRef} />
          <div className="mt-8 flex justify-center">
            <Button
              variant="secondary"
              onClick={() => setVisible((v) => Math.min(v + pageSize, listings.length))}
            >
              더 보기 ({remaining}개)
            </Button>
          </div>
        </>
      ) : null}
    </div>
  );
}
