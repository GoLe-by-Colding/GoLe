"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { formatWon } from "@entities/listing";
import {
  findUsableAcceptedOffer,
  OFFER_ERROR,
  offerErrorMessage,
  offerOrderAmount,
  offerTimeLeftLabel,
  useListingOffers,
} from "@entities/offer";
import { placeOrder } from "@entities/order";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { loginHrefForCurrentPage, useClock } from "@shared/lib";
import { Button, Field, Input, Text } from "@shared/ui";

export interface PurchaseButtonProps {
  readonly listingId: string;
  /** 매물 판매자 id. 자기 매물이면 구매 동선을 노출하지 않는다. */
  readonly sellerId: string;
  /** 지금 매물가. 수락 제안으로 주문할 때 실제 금액(`min(제안가, 매물가)`)과 정가 안내에 쓴다. */
  readonly listingPrice: number;
  readonly available: boolean;
}

/** 마지막으로 쓴 CS 연락처를 기억해 다음 구매에서 미리 채운다. */
const PHONE_STORAGE_KEY = "gole.buyer-phone";

function readStoredPhone(): string {
  if (typeof window === "undefined") return "";
  try {
    return window.localStorage.getItem(PHONE_STORAGE_KEY) ?? "";
  } catch {
    return "";
  }
}

/**
 * 매물 구매 버튼. 내게 유효한 수락 제안(채팅 네고·입찰 체결)이 있으면 "제안가 N원으로 구매"로 바꿔
 * 그 제안을 실어 주문한다(price-offer O16). 서버가 그 제안을 쓸 수 없다고 하면(409 `OFFER_NOT_USABLE`)
 * 예약이 풀린 상태이므로 정가 주문으로 이어 갈 수 있게 안내한다.
 */
export function PurchaseButton({
  listingId,
  sellerId,
  listingPrice,
  available,
}: PurchaseButtonProps) {
  const router = useRouter();
  const { session } = useSession();
  const now = useClock();
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);
  // 구매하기 → 연락처 확인 → 주문 생성. 배송 문제가 생기면 연락할 번호가 필요하다(R8.1).
  const [phoneStep, setPhoneStep] = useState(false);
  const [buyerPhone, setBuyerPhone] = useState("");
  // 서버가 제안을 거부하면 이 화면에서는 정가 주문으로 넘어간다.
  const [offerRejected, setOfferRejected] = useState(false);
  const isOwnListing = session?.accountId === sellerId;
  // 제안 조회는 세션이 필요하다. 비로그인·본인 매물·판매 끝난 매물이면 묻지 않는다.
  const { offers, reload: reloadOffers } = useListingOffers(listingId, {
    enabled: session !== null && !isOwnListing && available,
  });
  const usableOffer =
    session === null || offerRejected
      ? null
      : findUsableAcceptedOffer(offers ?? [], listingId, session.accountId, now ?? 0);
  const offerAmount =
    usableOffer === null ? null : offerOrderAmount(usableOffer.price, listingPrice);

  function handleClick() {
    if (!session) {
      router.push(loginHrefForCurrentPage());
      return;
    }
    setError(undefined);
    setBuyerPhone(readStoredPhone());
    setPhoneStep(true);
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!session || submitting) return;
    setError(undefined);
    setSubmitting(true);
    const offerId = usableOffer?.id;
    try {
      const phone = buyerPhone.trim();
      const order = await placeOrder(listingId, session.accountId, phone || undefined, offerId);
      try {
        if (phone) window.localStorage.setItem(PHONE_STORAGE_KEY, phone);
      } catch {
        // 저장 실패는 무시 — 다음 구매에서 다시 입력하면 된다.
      }
      router.push(`/orders/${order.id}`);
    } catch (cause) {
      if (
        offerId !== undefined &&
        cause instanceof ApiError &&
        cause.code === OFFER_ERROR.NOT_USABLE
      ) {
        // 서버가 예약을 풀었으므로 같은 매물을 정가로 다시 주문할 수 있다(O16).
        setOfferRejected(true);
        setError(
          `${offerErrorMessage(cause, "order")} 정가 ${formatWon(listingPrice)}으로 주문할 수 있어요.`,
        );
        reloadOffers();
      } else {
        setError(cause instanceof ApiError ? cause.message : "주문 생성 중 오류가 발생했습니다.");
      }
      setSubmitting(false);
    }
  }

  // 자기 매물은 구매할 수 없다(서버도 SELF_PURCHASE_NOT_ALLOWED로 거부한다).
  // 버튼을 비활성으로 남기면 왜 막혔는지 알 수 없어 이유를 함께 보여준다.
  if (isOwnListing) {
    return (
      <div className="flex flex-col gap-2">
        <Button size="lg" disabled>
          내 매물
        </Button>
        <span className="text-sm text-neutral-500">
          내가 등록한 매물은 구매할 수 없어요. 시세를 지키기 위한 정책이에요.
        </span>
      </div>
    );
  }

  if (phoneStep) {
    return (
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        {offerAmount !== null ? (
          <Text size="sm" tone="secondary">
            판매자가 수락한 제안가 {formatWon(offerAmount)}으로 주문해요. (정가{" "}
            {formatWon(listingPrice)})
          </Text>
        ) : null}
        <Field
          label="CS 연락처"
          hint="배송 문제가 생겼을 때 연락받을 번호예요. 판매자에게는 마스킹되어 보입니다."
        >
          {({ inputId, describedBy }) => (
            <Input
              id={inputId}
              aria-describedby={describedBy}
              value={buyerPhone}
              onChange={(e) => setBuyerPhone(e.target.value)}
              placeholder="010-1234-5678"
              inputMode="tel"
              type="tel"
              required
              autoFocus
            />
          )}
        </Field>
        {error ? (
          <Text size="sm" className="text-danger" role="alert">
            {error}
          </Text>
        ) : null}
        <div className="flex gap-2">
          <Button size="lg" fullWidth type="submit" disabled={submitting}>
            {submitting ? "처리 중..." : offerRejected ? "정가로 주문하기" : "주문하기"}
          </Button>
          <Button size="lg" variant="ghost" type="button" onClick={() => setPhoneStep(false)}>
            취소
          </Button>
        </div>
      </form>
    );
  }

  if (available && offerAmount !== null && usableOffer !== null) {
    const timeLeft = now === null ? null : offerTimeLeftLabel(usableOffer.expiresAt, now);
    return (
      <div className="flex flex-col gap-2" data-testid="purchase-with-offer">
        <Button size="lg" disabled={submitting} onClick={handleClick}>
          제안가 {formatWon(offerAmount)}으로 구매
        </Button>
        <span className="text-sm text-neutral-500">
          판매자가 수락한 가격이에요 · 정가 {formatWon(listingPrice)}
          {timeLeft === null ? "" : ` · ${timeLeft}`}
        </span>
        {error ? <span className="text-sm text-danger">{error}</span> : null}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      <Button size="lg" disabled={!available || submitting} onClick={handleClick}>
        {available ? "구매하기" : "거래완료"}
      </Button>
      {error ? <span className="text-sm text-danger">{error}</span> : null}
    </div>
  );
}
