"use client";

import { useState } from "react";
import Link from "next/link";
import { formatWon, type Listing } from "@entities/listing";
import {
  acceptOffer,
  declineOffer,
  isOfferOpen,
  offerDiscountPercent,
  offerErrorMessage,
  OfferStatusBadge,
  offerTimeLeftLabel,
  useListingOffers,
  type PriceOffer,
} from "@entities/offer";
import { cn, useClock } from "@shared/lib";
import { Button, Skeleton } from "@shared/ui";

/** 판매자 화면은 대화방처럼 메시지 신호가 없으므로 조금 느슨하게 폴링한다. */
const POLL_MS = 30_000;
/** 지난 제안은 최근 것만 보여 준다. 서버도 최신 50건까지만 준다. */
const MAX_ROWS = 10;

export interface ReceivedOffersSectionProps {
  readonly listing: Pick<Listing, "id" | "status">;
  /** 플랫폼 결제가 열려 있는지. 수락한 제안의 다음 단계 안내가 달라진다(price-offer O20). */
  readonly paymentsOpen: boolean;
  /** 바뀌면 다시 읽는다. 최고 입찰가에 판매해 새 수락 제안이 생긴 직후 등. */
  readonly refreshKey?: number;
}

type Response = "accept" | "decline";

/**
 * 매물 상세 판매자 패널의 "받은 가격 제안". 채팅 제안과 입찰 체결 제안을 함께 보여 주고,
 * 대기 제안은 수락·거절, 수락한 제안은 수락 취소할 수 있다. (price-offer F2)
 */
export function ReceivedOffersSection({
  listing,
  paymentsOpen,
  refreshKey = 0,
}: ReceivedOffersSectionProps) {
  const { offers, failed, apply, reload } = useListingOffers(listing.id, {
    pollMs: POLL_MS,
    refreshKey,
  });
  const now = useClock();
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const active = listing.status === "active";

  async function respond(offer: PriceOffer, response: Response) {
    if (busy !== null) return;
    setBusy(`${response}:${offer.id}`);
    setError(null);
    try {
      apply(await (response === "accept" ? acceptOffer(offer.id) : declineOffer(offer.id)));
    } catch (cause) {
      setError(offerErrorMessage(cause, response));
      reload();
    } finally {
      setBusy(null);
    }
  }

  const rows =
    offers === null
      ? []
      : [...offers.filter(isOfferOpen), ...offers.filter((offer) => !isOfferOpen(offer))].slice(
          0,
          MAX_ROWS,
        );

  return (
    <section
      aria-labelledby="listing-offers-heading"
      className="flex flex-col gap-3 p-4"
      data-testid="received-offers"
    >
      <div className="flex flex-col gap-0.5">
        <h2 id="listing-offers-heading" className="text-sm font-semibold text-neutral-900">
          받은 가격 제안
        </h2>
        <p className="text-xs leading-relaxed text-neutral-500">
          {paymentsOpen
            ? "수락하면 구매자가 72시간 안에 그 가격으로 주문할 수 있어요."
            : "수락한 가격은 채팅에서 '합의한 가격'으로 보이고, 직거래로 진행해요."}
        </p>
      </div>
      {offers === null ? (
        failed ? (
          <div className="flex items-center gap-2">
            <p className="text-sm text-neutral-500">받은 제안을 불러오지 못했어요.</p>
            <Button size="sm" variant="ghost" onClick={reload}>
              다시 시도
            </Button>
          </div>
        ) : (
          <Skeleton className="h-12 w-full rounded-md" />
        )
      ) : rows.length === 0 ? (
        <p className="text-sm text-neutral-500">
          아직 받은 제안이 없어요. 구매자가 채팅에서 가격을 제안하면 여기에 모여요.
        </p>
      ) : (
        <ul className="flex flex-col divide-y divide-neutral-100" aria-live="polite">
          {rows.map((offer) => {
            const open = isOfferOpen(offer);
            const discount = offerDiscountPercent(offer.listingPriceAtOffer, offer.price);
            const timeLeft = open && now !== null ? offerTimeLeftLabel(offer.expiresAt, now) : null;
            return (
              <li
                key={offer.id}
                className={cn(
                  "flex flex-wrap items-center justify-between gap-x-3 gap-y-2 py-2.5 first:pt-0 last:pb-0",
                  !open && "opacity-60",
                )}
              >
                <div className="flex min-w-0 flex-col gap-0.5">
                  <div className="flex items-center gap-2">
                    <OfferStatusBadge status={offer.status} />
                    <span className="text-base font-semibold tabular-nums text-neutral-900">
                      {formatWon(offer.price)}
                    </span>
                    {discount === null ? null : (
                      <span className="text-xs tabular-nums text-neutral-500">
                        {discount}% 할인
                      </span>
                    )}
                  </div>
                  <span className="text-xs text-neutral-500">
                    {offer.origin === "bid" ? "구매 입찰" : "채팅 제안"} · 구매자{" "}
                    {offer.buyerId.slice(0, 8)}
                    {timeLeft === null ? "" : ` · ${timeLeft}`}
                  </span>
                </div>
                <div className="flex shrink-0 items-center gap-1.5">
                  {offer.roomId !== null ? (
                    <Link
                      href={`/chat?room=${encodeURIComponent(offer.roomId)}`}
                      className="rounded-md px-2 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-50"
                    >
                      대화 보기
                    </Link>
                  ) : null}
                  {offer.status === "pending" && active ? (
                    <>
                      <Button
                        size="sm"
                        disabled={busy !== null}
                        onClick={() => void respond(offer, "accept")}
                      >
                        {busy === `accept:${offer.id}` ? "수락 중" : "수락"}
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        disabled={busy !== null}
                        onClick={() => void respond(offer, "decline")}
                      >
                        {busy === `decline:${offer.id}` ? "거절 중" : "거절"}
                      </Button>
                    </>
                  ) : null}
                  {offer.status === "accepted" ? (
                    <Button
                      size="sm"
                      variant="ghost"
                      disabled={busy !== null}
                      onClick={() => void respond(offer, "decline")}
                    >
                      {busy === `decline:${offer.id}` ? "취소 중" : "수락 취소"}
                    </Button>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {error !== null ? (
        <p role="alert" className="text-xs text-danger">
          {error}
        </p>
      ) : null}
    </section>
  );
}
