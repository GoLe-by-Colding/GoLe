"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { fetchSellerShop } from "@entities/discovery";
import { fetchSellerRating, type SellerRating } from "@entities/review";
import { StarIcon } from "@shared/ui";

export interface SellerMiniCardProps {
  readonly sellerId: string;
}

/**
 * 판매자 미니 프로필 — 매물 상세에서 신뢰 신호로 노출.
 * 아바타(이니셜) + 평균 평점·후기수 + 판매 중 매물 수 + 샵 링크.
 */
export function SellerMiniCard({ sellerId }: SellerMiniCardProps) {
  const [rating, setRating] = useState<SellerRating | null>(null);
  const [listingCount, setListingCount] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    const ctrl = new AbortController();
    void (async () => {
      const [r, shop] = await Promise.allSettled([
        fetchSellerRating(sellerId, ctrl.signal),
        fetchSellerShop(sellerId, ctrl.signal),
      ]);
      if (!active) return;
      if (r.status === "fulfilled") setRating(r.value);
      if (shop.status === "fulfilled") setListingCount(shop.value.length);
    })();
    return () => {
      active = false;
      ctrl.abort();
    };
  }, [sellerId]);

  const hasRating = rating !== null && rating.count > 0;

  return (
    <Link
      href={`/shops/${sellerId}`}
      className="group flex items-center gap-3 rounded-lg border border-neutral-200 bg-white p-3 transition-colors hover:border-brand-300 hover:bg-brand-50/40"
    >
      <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-brand-50 text-base font-bold text-brand-700">
        {sellerId.slice(0, 1).toUpperCase()}
      </span>
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="truncate text-sm font-semibold text-neutral-900">
          {sellerId.slice(0, 8)} 님의 샵
        </span>
        <span className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-neutral-500">
          {hasRating ? (
            <span
              className="inline-flex items-center gap-1 font-semibold text-warning"
              aria-label={`평점 ${rating.average.toFixed(1)}점, 후기 ${rating.count}개`}
            >
              <StarIcon className="h-3.5 w-3.5" filled />
              <span aria-hidden="true">{rating.average.toFixed(1)}</span>
              <span className="ml-0.5 font-normal text-neutral-400">({rating.count})</span>
            </span>
          ) : (
            <span className="text-neutral-400">아직 후기 없음</span>
          )}
          {listingCount !== null ? (
            <>
              <span aria-hidden="true" className="text-neutral-300">
                ·
              </span>
              <span>판매 중 {listingCount}개</span>
            </>
          ) : null}
        </span>
      </div>
      <span
        aria-hidden="true"
        className="ml-auto shrink-0 text-neutral-300 transition-colors group-hover:text-brand-500"
      >
        →
      </span>
    </Link>
  );
}
