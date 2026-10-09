"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  bidBookCondition,
  bidErrorMessage,
  fetchBidBook,
  fillBid,
  type BidBookCondition,
  type FillBidResult,
} from "@entities/bid";
import {
  conditionLabel,
  formatWon,
  listingMutationErrorMessage,
  updateListing,
  type Listing,
} from "@entities/listing";
import { Button } from "@shared/ui";
import { draftFromListing, draftToInput } from "../model/listing-draft";

export interface SellToBidSectionProps {
  /** 입찰가에 맞춰 판매가를 올릴 때 수정 본문(제목·사진 등)을 그대로 다시 보내야 해서 전체를 받는다. */
  readonly listing: Listing;
  /** 판매에 성공하면 부른다. 받은 제안 목록이 새 수락 제안을 바로 보이게 한다. */
  readonly onFilled?: (result: FillBidResult) => void;
}

interface BookRow {
  readonly key: string;
  readonly row: BidBookCondition | null;
}

/**
 * 매물 상세 판매자 패널의 "구매 입찰" — 그 세트·상태에 걸린 입찰이 있으면 최고 입찰가에 바로 판다.
 * (buy-bids D7·F1)
 *
 * 판매하면 입찰자에게 72시간 유효한 수락 제안이 생기고, 구매자가 그 가격으로 주문한다(결제 단계)
 * 또는 그 가격으로 직거래한다. 호가창은 공개 조회라 본인 입찰도 섞여 있지만 서버는 본인 입찰을
 * 빼고 고르므로, 실제 판매가는 응답의 `bidPrice`로 안내한다.
 *
 * 주문 금액은 `min(입찰가, 판매가)`다(price-offer O17). 그래서 입찰가가 판매가보다 높으면 "입찰가에
 * 판매"라고만 쓰면 판매자는 입찰가를 받는다고 오해한다(2026-10-09 로컬 QA). 이때는 판매가를 입찰가로
 * 올린 뒤 체결하는 것을 주 동작으로, 지금 판매가 그대로 체결하는 것을 보조 동작으로 둔다.
 */
export function SellToBidSection({ listing, onFilled }: SellToBidSectionProps) {
  const router = useRouter();
  const setNumber = listing.catalogSetNumber;
  const sellable = setNumber !== null && listing.status === "active";
  const bookKey = `${setNumber ?? ""}:${listing.condition}`;
  const [book, setBook] = useState<BookRow | null>(null);
  const [nonce, setNonce] = useState(0);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<FillBidResult | null>(null);
  /** 입찰가에 맞춰 올린 판매가. 올리지 않고 팔았으면 null. */
  const [raisedPrice, setRaisedPrice] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (setNumber === null || listing.status !== "active") return;
    const controller = new AbortController();
    fetchBidBook(setNumber, controller.signal).then(
      (found) => {
        if (controller.signal.aborted) return;
        setBook({ key: bookKey, row: bidBookCondition(found, listing.condition) });
      },
      () => {
        // 호가창을 못 읽으면 이 구획을 감춘다. 판매 동선의 다른 부분을 막지 않는다.
        if (!controller.signal.aborted) setBook({ key: bookKey, row: null });
      },
    );
    return () => controller.abort();
  }, [bookKey, listing.condition, listing.status, nonce, setNumber]);

  if (!sellable) return null;
  const row = book?.key === bookKey ? book.row : null;
  if (row === null && result === null) return null;

  const highest = row?.highestPrice ?? null;

  const bidAboveAsk = highest !== null && highest > listing.price;

  /**
   * @param raiseTo 체결 전에 판매가를 이 값으로 올린다. null이면 지금 판매가 그대로 체결한다.
   */
  async function handleFill(raiseTo: number | null) {
    if (busy || setNumber === null || highest === null) return;
    const confirmed = window.confirm(
      raiseTo !== null
        ? `판매가를 ${formatWon(raiseTo)}으로 올리고 최고 입찰가에 판매할까요?\n\n` +
            `입찰자에게 72시간 유효한 수락 제안이 생기고, 주문 금액은 ${formatWon(raiseTo)}이에요. ` +
            "내가 건 입찰은 제외돼요."
        : `최고 입찰가 ${formatWon(highest)}에 이 매물을 판매할까요?\n\n` +
            "입찰자에게 72시간 유효한 수락 제안이 생기고, 구매자가 그 가격으로 거래를 진행해요. " +
            (highest > listing.price
              ? `주문 금액은 지금 판매가 ${formatWon(listing.price)}이에요. `
              : "") +
            "내가 건 입찰은 제외돼요.",
    );
    if (!confirmed) return;
    setBusy(true);
    setError(null);
    if (raiseTo !== null) {
      try {
        await updateListing(
          listing.id,
          draftToInput({ ...draftFromListing(listing), price: String(raiseTo) }),
        );
        setRaisedPrice(raiseTo);
      } catch (cause) {
        setError(listingMutationErrorMessage(cause, "edit"));
        setBusy(false);
        return;
      }
    }
    try {
      const filled = await fillBid(setNumber, listing.id);
      setResult(filled);
      onFilled?.(filled);
    } catch (cause) {
      setError(
        raiseTo !== null
          ? `판매가는 ${formatWon(raiseTo)}으로 올렸지만 판매하지 못했어요. ${bidErrorMessage(cause, "fill")}`
          : bidErrorMessage(cause, "fill"),
      );
      setNonce((current) => current + 1);
    } finally {
      setBusy(false);
      // 올린 판매가를 상단 가격·패널에 반영한다(서버 렌더 매물 정보).
      if (raiseTo !== null) router.refresh();
    }
  }

  const soldAt = raisedPrice ?? listing.price;

  return (
    <section
      aria-labelledby="listing-bids-heading"
      className="flex flex-col gap-3 p-4"
      data-testid="sell-to-bid"
    >
      <div className="flex flex-col gap-0.5">
        <h2 id="listing-bids-heading" className="text-sm font-semibold text-neutral-900">
          구매 입찰
        </h2>
        <p className="text-xs leading-relaxed text-neutral-500">
          #{setNumber} · {conditionLabel(listing.condition)} 상태로 사겠다고 걸어 둔 입찰이에요.
        </p>
      </div>
      {result !== null ? (
        <p role="status" className="rounded-md bg-success-soft px-3 py-2 text-sm text-success">
          입찰가 {formatWon(result.bidPrice)}에 판매를 수락했어요. 입찰자에게 72시간 유효한 수락
          제안이 생겼어요.
          {result.bidPrice > soldAt ? ` 주문 금액은 판매가 ${formatWon(soldAt)}이에요.` : ""}
        </p>
      ) : row !== null && highest !== null ? (
        <div className="flex flex-col items-start gap-2">
          <p className="text-sm text-neutral-700">
            입찰 {row.bidCount}건 · 최고 입찰가{" "}
            <span className="font-semibold tabular-nums text-neutral-900">
              {formatWon(highest)}
            </span>
          </p>
          {bidAboveAsk ? (
            <>
              <p className="text-xs leading-relaxed text-neutral-600">
                최고 입찰가가 지금 판매가 {formatWon(listing.price)}보다{" "}
                {formatWon(highest - listing.price)} 높아요. 주문 금액은 판매가를 넘지 않으니,
                판매가를 입찰가로 올려 파는 편이 유리해요.
              </p>
              <Button size="sm" disabled={busy} onClick={() => void handleFill(highest)}>
                {busy ? "판매 중…" : `판매가를 ${formatWon(highest)}으로 올리고 바로 판매`}
              </Button>
              <button
                type="button"
                disabled={busy}
                onClick={() => void handleFill(null)}
                className="text-xs text-neutral-500 underline underline-offset-2 hover:text-neutral-700 disabled:opacity-50"
              >
                판매가 {formatWon(listing.price)} 그대로 판매
              </button>
            </>
          ) : (
            <Button size="sm" disabled={busy} onClick={() => void handleFill(null)}>
              {busy ? "판매 중…" : `최고 입찰가 ${formatWon(highest)}에 바로 판매`}
            </Button>
          )}
        </div>
      ) : (
        <p className="text-sm text-neutral-500">
          아직 이 세트·상태에 걸린 입찰이 없어요. 입찰이 들어오면 여기서 바로 팔 수 있어요.
        </p>
      )}
      {error !== null ? (
        <p role="alert" className="text-xs text-danger">
          {error}
        </p>
      ) : null}
    </section>
  );
}
