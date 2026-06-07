"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { fetchSellerShop, type ListingSummary } from "@entities/discovery";
import { FollowButton } from "@features/follow-seller";
import { formatKrw } from "@shared/lib";
import { Card, Container, Heading, Text } from "@shared/ui";

export interface SellerShopPageProps {
  readonly sellerId: string;
}

export function SellerShopPage({ sellerId }: SellerShopPageProps) {
  const [listings, setListings] = useState<readonly ListingSummary[]>([]);

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const data = await fetchSellerShop(sellerId);
        if (active) {
          setListings(data);
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
            <Text tone="secondary">판매 중인 상품 {listings.length}개</Text>
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
      </div>
    </Container>
  );
}
