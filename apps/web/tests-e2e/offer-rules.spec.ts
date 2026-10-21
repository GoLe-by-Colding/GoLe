import { expect, test } from "@playwright/test";
import { findUsableAcceptedOffer, type PriceOffer } from "@gole/core/offer";

const NOW = Date.parse("2026-10-21T00:00:00Z");

function offer(overrides: Partial<PriceOffer> = {}): PriceOffer {
  return {
    id: "first",
    listingId: "listing-1",
    roomId: null,
    buyerId: "buyer-1",
    sellerId: "seller-1",
    price: 90_000,
    listingPriceAtOffer: 100_000,
    origin: "chat",
    status: "accepted",
    createdAt: "2026-10-20T00:00:00Z",
    respondedAt: "2026-10-20T01:00:00Z",
    expiresAt: "2026-10-22T00:00:00Z",
    ...overrides,
  };
}

test("같은 구매자가 먼저 보낸 유효한 수락 제안을 가격과 응답 순서에 관계없이 고른다", () => {
  const first = offer();
  const later = offer({ id: "later", createdAt: "2026-10-20T02:00:00Z", price: 80_000 });
  for (const offers of [
    [later, first],
    [first, later],
  ]) {
    expect(findUsableAcceptedOffer(offers, "listing-1", "buyer-1", NOW)?.id).toBe("first");
  }
});

test("생성 시각이 같으면 ID 순서로 선택을 유지한다", () => {
  const first = offer({ id: "a" });
  const second = offer({ id: "b" });
  for (const offers of [
    [second, first],
    [first, second],
  ]) {
    expect(findUsableAcceptedOffer(offers, "listing-1", "buyer-1", NOW)?.id).toBe("a");
  }
});

test("다른 매물·구매자의 제안과 만료·철회·대기 제안은 먼저 와도 선택하지 않는다", () => {
  const invalid = [
    offer({ listingId: "other-listing" }),
    offer({ buyerId: "other-buyer" }),
    offer({ expiresAt: new Date(NOW).toISOString() }),
    offer({ status: "withdrawn" }),
    offer({ status: "pending" }),
  ];
  const next = offer({ id: "next", createdAt: "2026-10-20T03:00:00Z" });
  expect(findUsableAcceptedOffer([...invalid, next], "listing-1", "buyer-1", NOW)?.id).toBe("next");
  expect(findUsableAcceptedOffer(invalid, "listing-1", "buyer-1", NOW)).toBeNull();
});
