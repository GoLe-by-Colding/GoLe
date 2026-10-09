"use client";

import { type FormEvent, useEffect, useId, useState } from "react";
import { fetchListingById, formatWon, type ListingStatus } from "@entities/listing";
import {
  acceptOffer,
  declineOffer,
  isOfferOpen,
  makeOffer,
  OFFER_ERROR,
  offerDiscountPercent,
  offerErrorMessage,
  OfferStatusBadge,
  offerTimeLeftLabel,
  useListingOffers,
  validateOfferPrice,
  withdrawOffer,
  type PriceOffer,
} from "@entities/offer";
import {
  isThirdPartyProvisionConsentCancelledError,
  type ThirdPartyProvisionPath,
} from "@entities/user";
import { ApiError } from "@shared/api";
import { useClock } from "@shared/lib";
import { Button, LinkButton } from "@shared/ui";

type ConsentRunner = <T>(action: () => Promise<T>, path: ThirdPartyProvisionPath) => Promise<T>;
type Response = "accept" | "decline" | "withdraw";

/** 상대가 응답하면 방에 메시지가 남아 바로 다시 읽지만, 스트림이 끊긴 경우를 위해 폴링도 둔다. */
const POLL_MS = 10_000;

export interface ListingOfferPanelProps {
  readonly roomId: string;
  readonly listingId: string;
  readonly buyerId: string;
  readonly sellerId: string;
  readonly myId: string;
  /**
   * 플랫폼 결제가 열려 있는지(`isPurchaseOpen`). 열려 있으면 수락된 제안에 "제안가로 구매하기"를,
   * 닫혀 있으면(직거래 단계) "합의한 가격 — 직거래로 진행"을 보인다(price-offer O20).
   */
  readonly paymentsOpen: boolean;
  /**
   * 수락된 제안에 매물 상세로 가는 "제안가로 구매하기" 링크를 둘지. 매물 상세 안의 인라인 채팅은
   * 이미 그 화면이라 `false`로 두고, 같은 화면의 구매 버튼이 제안가로 바뀐다고 안내한다.
   */
  readonly purchaseLink?: boolean;
  /**
   * 바뀔 때마다 제안을 다시 읽는다. 방의 마지막 메시지 id를 넘긴다 — 제안·수락·거절·철회마다 방에
   * 메시지가 남으므로(O6~O9) 상대의 응답을 폴링을 기다리지 않고 반영한다.
   */
  readonly refreshKey?: string | null;
  /** 제안 생성은 채팅 메시지와 같은 제3자 제공 동의를 요구한다(O2). 호출부의 동의 대화상자로 감싼다. */
  readonly runWithConsent: ConsentRunner;
}

interface ListingSummary {
  readonly id: string;
  readonly price: number;
  readonly status: ListingStatus;
}

/**
 * 매물 대화방 메시지 목록 위의 가격 제안 배너. (price-offer F2)
 *
 * - 구매자: 가격 제안(매물가·할인율 표시), 진행 중 제안 상태, 철회.
 * - 판매자: 대기 제안 수락·거절, 수락한 제안 취소.
 * - 수락됨: 결제가 열려 있으면 매물 상세의 "제안가로 구매"로, 직거래 단계면 합의 가격 안내.
 *
 * 제안은 방이 아니라 매물 기준으로 읽는다(`?listingId=`). 입찰 체결로 생긴 수락 제안은 방이 없어
 * 방 기준 조회에 빠지는데, 직거래 단계에서는 이 대화방이 그 가격으로 거래를 이어 가는 곳이기 때문이다.
 * 판매자 조회는 매물의 모든 구매자 제안을 주므로 이 방의 구매자 것만 남긴다.
 */
export function ListingOfferPanel({
  roomId,
  listingId,
  buyerId,
  sellerId,
  myId,
  paymentsOpen,
  purchaseLink = true,
  refreshKey = null,
  runWithConsent,
}: ListingOfferPanelProps) {
  const isBuyer = myId === buyerId;
  const isSeller = myId === sellerId;
  const { offers, apply, reload } = useListingOffers(listingId, {
    enabled: isBuyer || isSeller,
    pollMs: POLL_MS,
    refreshKey,
  });
  const now = useClock();
  const priceInputId = useId();
  const [listing, setListing] = useState<ListingSummary | null>(null);
  const [listingNonce, setListingNonce] = useState(0);
  const [composing, setComposing] = useState(false);
  const [priceInput, setPriceInput] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // 할인율과 "판매가보다 낮게" 검사를 위한 매물가. 못 읽어도 제안은 할 수 있다 — 서버가 검사한다.
  useEffect(() => {
    if (!isBuyer) return;
    const controller = new AbortController();
    fetchListingById(listingId, controller.signal).then(
      (found) => {
        if (!controller.signal.aborted) {
          setListing({ id: found.id, price: found.price, status: found.status });
        }
      },
      () => undefined,
    );
    return () => controller.abort();
  }, [isBuyer, listingId, listingNonce]);

  if ((!isBuyer && !isSeller) || offers === null) return null;

  const currentListing = listing?.id === listingId ? listing : null;
  const roomOffers = offers.filter(
    (offer) => offer.buyerId === buyerId && offer.sellerId === sellerId,
  );
  const openOffers = roomOffers.filter(isOfferOpen);
  const lastClosed = openOffers.length === 0 ? (roomOffers[0] ?? null) : null;
  const canPropose =
    isBuyer &&
    openOffers.length === 0 &&
    (currentListing === null || currentListing.status === "active");

  // 판매자에게는 응답할 제안이 있을 때만 보인다. 대화를 가리지 않는다.
  if (openOffers.length === 0 && !canPropose) return null;

  const typedPrice = priceInput.trim().length === 0 ? null : Number(priceInput);
  const discount =
    currentListing === null || typedPrice === null
      ? null
      : offerDiscountPercent(currentListing.price, typedPrice);

  async function submitOffer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy !== null) return;
    const price = Number(priceInput.trim());
    const invalid = validateOfferPrice(price, currentListing?.price ?? null);
    if (invalid !== undefined) {
      setError(invalid);
      return;
    }
    setBusy("make");
    setError(null);
    try {
      const created = await runWithConsent(() => makeOffer(roomId, price), "CHAT_MESSAGE");
      apply(created);
      setComposing(false);
      setPriceInput("");
    } catch (cause) {
      if (isThirdPartyProvisionConsentCancelledError(cause)) return;
      setError(offerErrorMessage(cause, "make"));
      if (cause instanceof ApiError) {
        if (cause.code === OFFER_ERROR.ALREADY_PENDING) reload();
        if (
          cause.code === OFFER_ERROR.PRICE_INVALID ||
          cause.code === OFFER_ERROR.LISTING_UNAVAILABLE
        ) {
          setListingNonce((current) => current + 1);
        }
      }
    } finally {
      setBusy(null);
    }
  }

  async function respond(offer: PriceOffer, response: Response) {
    if (busy !== null) return;
    setBusy(`${response}:${offer.id}`);
    setError(null);
    try {
      const run =
        response === "accept" ? acceptOffer : response === "decline" ? declineOffer : withdrawOffer;
      apply(await run(offer.id));
    } catch (cause) {
      setError(offerErrorMessage(cause, response));
      // 상대가 먼저 철회·응답했을 수 있다(O11). 지금 상태로 다시 맞춘다.
      reload();
    } finally {
      setBusy(null);
    }
  }

  return (
    <section
      aria-label="가격 제안"
      data-testid="listing-offer-panel"
      className="flex flex-col gap-2 border-b border-neutral-200 bg-neutral-50/70 px-4 py-2.5"
    >
      <div aria-live="polite" className="flex flex-col gap-2">
        {openOffers.map((offer) => (
          <OfferRow
            key={offer.id}
            offer={offer}
            viewer={isBuyer ? "buyer" : "seller"}
            paymentsOpen={paymentsOpen}
            purchaseLink={purchaseLink}
            now={now}
            busy={busy}
            onRespond={(response) => void respond(offer, response)}
          />
        ))}
        {canPropose && !composing ? (
          <div className="flex items-center justify-between gap-3">
            <p className="min-w-0 text-xs text-neutral-600">
              {lastClosed === null
                ? "원하는 가격이 있으면 판매자에게 제안해 보세요."
                : `지난 제안 ${formatWon(lastClosed.price)}은 ${closedLabel(lastClosed)}.`}
            </p>
            <Button
              size="sm"
              variant="secondary"
              className="shrink-0"
              onClick={() => {
                setError(null);
                setComposing(true);
              }}
            >
              가격 제안
            </Button>
          </div>
        ) : null}
      </div>
      {canPropose && composing ? (
        <form onSubmit={(event) => void submitOffer(event)} className="flex flex-col gap-1.5">
          <label htmlFor={priceInputId} className="text-xs font-semibold text-neutral-700">
            제안 금액 (원)
          </label>
          <div className="flex items-center gap-2">
            <input
              id={priceInputId}
              type="number"
              inputMode="numeric"
              min={1}
              step={1}
              value={priceInput}
              onChange={(event) => setPriceInput(event.target.value)}
              placeholder={
                currentListing === null
                  ? "금액 입력"
                  : `${formatWon(currentListing.price)}보다 낮게`
              }
              className="h-9 min-w-0 flex-1 rounded-md border border-neutral-200 bg-white px-3 text-sm tabular-nums outline-none focus-visible:border-brand-400 focus-visible:ring-2 focus-visible:ring-brand-100"
              autoFocus
            />
            <Button type="submit" size="sm" className="shrink-0" disabled={busy !== null}>
              {busy === "make" ? "보내는 중" : "제안하기"}
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="shrink-0"
              disabled={busy !== null}
              onClick={() => {
                setComposing(false);
                setPriceInput("");
                setError(null);
              }}
            >
              취소
            </Button>
          </div>
          {currentListing !== null ? (
            <p className="text-xs tabular-nums text-neutral-500">
              판매가 {formatWon(currentListing.price)}
              {discount === null ? "" : ` · ${discount}% 할인`}
            </p>
          ) : null}
        </form>
      ) : null}
      {error !== null ? (
        <p role="alert" className="text-xs text-danger">
          {error}
        </p>
      ) : null}
    </section>
  );
}

function closedLabel(offer: PriceOffer): string {
  switch (offer.status) {
    case "declined":
      return "판매자가 거절했어요";
    case "withdrawn":
      return "철회했어요";
    case "expired":
      return "응답 기한이 지나 만료됐어요";
    default:
      return "끝났어요";
  }
}

interface OfferRowProps {
  readonly offer: PriceOffer;
  readonly viewer: "buyer" | "seller";
  readonly paymentsOpen: boolean;
  readonly purchaseLink: boolean;
  readonly now: number | null;
  readonly busy: string | null;
  readonly onRespond: (response: Response) => void;
}

function OfferRow({
  offer,
  viewer,
  paymentsOpen,
  purchaseLink,
  now,
  busy,
  onRespond,
}: OfferRowProps) {
  const price = formatWon(offer.price);
  const discount = offerDiscountPercent(offer.listingPriceAtOffer, offer.price);
  const timeLeft = now === null ? null : offerTimeLeftLabel(offer.expiresAt, now);
  const pending = offer.status === "pending";
  const fromBid = offer.origin === "bid";
  const rowBusy = busy !== null;

  const headline =
    viewer === "buyer"
      ? pending
        ? `${price} 제안 · 판매자 응답을 기다리고 있어요`
        : fromBid
          ? `판매자가 입찰가 ${price}에 판매를 수락했어요`
          : `판매자가 ${price} 제안을 수락했어요`
      : pending
        ? `구매자가 ${price}을 제안했어요`
        : fromBid
          ? `입찰가 ${price}에 판매를 수락했어요`
          : `${price} 제안을 수락했어요`;

  const detail = [
    `판매가 ${formatWon(offer.listingPriceAtOffer)}`,
    discount === null ? null : `${discount}% 할인`,
    timeLeft,
  ]
    .filter((part): part is string => part !== null)
    .join(" · ");

  return (
    <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
      <div className="flex min-w-0 flex-col gap-0.5">
        <div className="flex min-w-0 items-center gap-2">
          <OfferStatusBadge status={offer.status} />
          <p className="min-w-0 text-sm font-semibold text-neutral-900">{headline}</p>
        </div>
        <p className="text-xs tabular-nums text-neutral-500">{detail}</p>
        {!pending ? (
          <p className="text-xs font-medium text-success">
            {paymentsOpen
              ? viewer === "buyer"
                ? purchaseLink
                  ? "매물 화면에서 제안가로 구매할 수 있어요."
                  : "위의 구매 버튼이 제안가로 바뀌었어요."
                : "구매자가 제안가로 구매할 수 있어요."
              : `합의한 가격 ${price} — 직거래로 진행해요.`}
          </p>
        ) : null}
      </div>
      <div className="flex shrink-0 items-center gap-1.5">
        {viewer === "buyer" && !pending && paymentsOpen && purchaseLink ? (
          <LinkButton href={`/listings/${encodeURIComponent(offer.listingId)}`} size="sm">
            제안가로 구매하기
          </LinkButton>
        ) : null}
        {viewer === "seller" && pending ? (
          <>
            <Button size="sm" disabled={rowBusy} onClick={() => onRespond("accept")}>
              {busy === `accept:${offer.id}` ? "수락 중" : "수락"}
            </Button>
            <Button
              size="sm"
              variant="ghost"
              disabled={rowBusy}
              onClick={() => onRespond("decline")}
            >
              {busy === `decline:${offer.id}` ? "거절 중" : "거절"}
            </Button>
          </>
        ) : null}
        {viewer === "seller" && !pending ? (
          <Button size="sm" variant="ghost" disabled={rowBusy} onClick={() => onRespond("decline")}>
            {busy === `decline:${offer.id}` ? "취소 중" : "수락 취소"}
          </Button>
        ) : null}
        {viewer === "buyer" ? (
          <Button
            size="sm"
            variant="ghost"
            disabled={rowBusy}
            onClick={() => onRespond("withdraw")}
          >
            {busy === `withdraw:${offer.id}` ? "철회 중" : "철회"}
          </Button>
        ) : null}
      </div>
    </div>
  );
}
