"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { fetchSellerShop, type ListingSummary } from "@entities/discovery";
import {
  fetchSellerRating,
  fetchSellerReviews,
  type Review,
  type SellerRating,
} from "@entities/review";
import { FollowButton } from "@features/follow-seller";
import { formatKrw } from "@shared/lib";
import { Badge, Card, Container, Heading, Text } from "@shared/ui";

export interface SellerShopPageProps {
  readonly sellerId: string;
}

export function SellerShopPage({ sellerId }: SellerShopPageProps) {
  const [listings, setListings] = useState<readonly ListingSummary[]>([]);
  const [rating, setRating] = useState<SellerRating | null>(null);
  const [reviews, setReviews] = useState<readonly Review[]>([]);

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const [shop, ratingSummary, reviewList] = await Promise.all([
          fetchSellerShop(sellerId),
          fetchSellerRating(sellerId),
          fetchSellerReviews(sellerId),
        ]);
        if (active) {
          setListings(shop);
          setRating(ratingSummary);
          setReviews(reviewList);
        }
      } catch {
        /* ignore */
      }
    })();
    return () => {
      active = false;
    };
  }, [sellerId]);

  return (
    <Container width="xl">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <div className="flex items-center justify-between gap-4">
          <div className="flex flex-col gap-1">
            <Heading level={1}>{sellerId.slice(0, 8)} 님의 샵</Heading>
            <div className="flex items-center gap-3">
              <Text tone="secondary">판매 중인 상품 {listings.length}개</Text>
              {rating !== null && rating.count > 0 ? (
                <Badge tone="warning" data-testid="seller-rating">
                  ★ {rating.average.toFixed(1)} ({rating.count})
                </Badge>
              ) : (
                <Text tone="muted">후기 없음</Text>
              )}
            </div>
          </div>
          <FollowButton sellerId={sellerId} />
        </div>

        {listings.length === 0 ? (
          <Text tone="muted">판매 중인 상품이 없습니다.</Text>
        ) : (
          <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(200px,1fr))]">
            {listings.map((l) => (
              <Link key={l.id} href={`/listings/${l.id}`}>
                <Card interactive padded className="flex flex-col gap-2">
                  <span className="text-sm font-semibold text-neutral-900 line-clamp-1">
                    {l.title}
                  </span>
                  <span className="text-lg font-bold">{formatKrw(l.price)}</span>
                  {l.catalogSetNumber !== null ? (
                    <span className="font-mono text-xs text-neutral-500">
                      #{l.catalogSetNumber}
                    </span>
                  ) : null}
                </Card>
              </Link>
            ))}
          </div>
        )}

        <section className="flex flex-col gap-3 pt-4">
          <Heading level={2}>거래 후기</Heading>
          {reviews.length === 0 ? (
            <Text tone="muted">아직 등록된 후기가 없습니다.</Text>
          ) : (
            <ul className="flex flex-col gap-3">
              {reviews.map((r) => (
                <li key={r.id}>
                  <Card padded className="flex flex-col gap-2">
                    <div className="flex items-center justify-between">
                      <span className="text-warning text-sm font-semibold">
                        {"★".repeat(r.rating)}
                        <span className="text-neutral-300">{"★".repeat(5 - r.rating)}</span>
                      </span>
                      <span className="text-xs text-neutral-400">
                        {new Date(r.createdAt).toLocaleDateString("ko-KR")}
                      </span>
                    </div>
                    <Text>{r.content}</Text>
                    <span className="font-mono text-xs text-neutral-400">
                      {r.reviewerId.slice(0, 8)}
                    </span>
                  </Card>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </Container>
  );
}
