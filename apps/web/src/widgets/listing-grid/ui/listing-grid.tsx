"use client";

import { useRef, useState, useEffect } from "react";
import Link from "next/link";
import { ListingCard, type Listing } from "@entities/listing";
import { Button, Text } from "@shared/ui";

export interface ListingGridProps {
  readonly listings: readonly Listing[];
  readonly emptyMessage?: string;
  /** 초기 노출 수. 기본 20. */
  readonly pageSize?: number;
}

/**
 * 클라이언트 측 점진적 로드(더 보기). 백엔드 페이지네이션 없이 브라우저에서 슬라이싱한다.
 * IntersectionObserver로 스크롤 말단 감지 시 자동으로 다음 배치를 노출한다.
 */
export function ListingGrid({
  listings,
  emptyMessage = "표시할 상품이 없습니다.",
  pageSize = 20,
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
      <div className="p-12 text-center">
        <Text tone="muted">{emptyMessage}</Text>
      </div>
    );
  }

  const shown = listings.slice(0, visible);
  const remaining = listings.length - visible;

  return (
    <div>
      <div
        className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(200px,1fr))]"
        data-testid="listing-grid"
      >
        {shown.map((listing) => (
          <Link key={listing.id} href={`/listings/${listing.id}`}>
            <ListingCard listing={listing} />
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
