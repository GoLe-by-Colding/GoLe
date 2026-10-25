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
import { conditionLabel, formatWon, type Listing } from "@entities/listing";
import { Button, Skeleton, useDialog } from "@shared/ui";

export interface SellToBidSectionProps {
  readonly listing: Listing;
  /** 판매에 성공하면 부른다. 받은 제안 목록이 새 수락 제안을 바로 보이게 한다. */
  readonly onFilled?: (result: FillBidResult) => void;
}

interface BookSnapshot {
  readonly key: string;
  readonly request: number;
}

type BookState = BookSnapshot &
  ({ readonly status: "ready"; readonly row: BidBookCondition } | { readonly status: "error" });

const SALE_ACTION_CLASS = "h-auto min-h-11 w-full px-3 py-3 leading-snug break-keep sm:h-auto";

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
 * 올리면서 체결하는 것을 주 동작으로, 지금 판매가 그대로 체결하는 것을 보조 동작으로 둔다.
 */
export function SellToBidSection({ listing, onFilled }: SellToBidSectionProps) {
  const router = useRouter();
  // 세트 입찰은 세트 한 벌로만 체결한다(서버 `Listing.wholeSetNumber`와 같은 규칙). 출처 세트 번호를 단
  // 미니피규어·부품 매물에는 이 구획을 보이지 않는다 — 서버도 "맞지 않는 매물"로 거절한다.
  const setNumber = listing.category === "set" ? listing.catalogSetNumber : null;
  const sellable = setNumber !== null && listing.status === "active";
  const bookKey = `${setNumber ?? ""}:${listing.condition}`;
  const [book, setBook] = useState<BookState | null>(null);
  const [nonce, setNonce] = useState(0);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<FillBidResult | null>(null);
  /** 체결과 함께 입찰가로 올린 판매가. 올리지 않고 팔았으면 null. */
  const [raisedPrice, setRaisedPrice] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const dialog = useDialog();

  useEffect(() => {
    if (setNumber === null || listing.status !== "active") return;
    const controller = new AbortController();
    fetchBidBook(setNumber, controller.signal).then(
      (found) => {
        if (controller.signal.aborted) return;
        setBook({
          key: bookKey,
          request: nonce,
          status: "ready",
          row: bidBookCondition(found, listing.condition),
        });
      },
      () => {
        if (!controller.signal.aborted) {
          setBook({ key: bookKey, request: nonce, status: "error" });
        }
      },
    );
    return () => controller.abort();
  }, [bookKey, listing.condition, listing.status, nonce, setNumber]);

  if (!sellable) return null;
  // 새 조회가 시작되는 렌더부터 지난 가격의 판매 버튼을 닫는다.
  const currentBook = book?.key === bookKey && book.request === nonce ? book : null;
  const loading = currentBook === null;
  const failed = currentBook?.status === "error";
  const row = currentBook?.status === "ready" ? currentBook.row : null;

  const highest = row?.highestPrice ?? null;

  const bidAboveAsk = highest !== null && highest > listing.price;

  /**
   * @param raiseTo 체결과 함께 판매가를 이 값으로 올린다. null이면 지금 판매가 그대로 체결한다.
   */
  async function handleFill(raiseTo: number | null) {
    if (busy || setNumber === null || highest === null) return;
    const confirmed = await dialog.confirm({
      title: raiseTo !== null ? "판매가를 올리고 판매" : "입찰가에 판매",
      confirmLabel: "판매",
      description:
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
    });
    if (!confirmed) return;
    setBusy(true);
    setError(null);
    try {
      const filled = await fillBid(setNumber, listing.id, raiseTo ?? undefined);
      setRaisedPrice(raiseTo);
      setResult(filled);
      onFilled?.(filled);
    } catch (cause) {
      setError(bidErrorMessage(cause, "fill"));
      setNonce((current) => current + 1);
    } finally {
      setBusy(false);
      if (raiseTo !== null) router.refresh();
    }
  }

  const soldAt = raisedPrice ?? listing.price;

  return (
    <section
      aria-labelledby="listing-bids-heading"
      className="flex min-w-0 flex-col gap-4 p-4"
      data-testid="sell-to-bid"
    >
      <div className="flex flex-col gap-1">
        <h2 id="listing-bids-heading" className="font-display text-base font-bold text-neutral-900">
          구매 입찰
        </h2>
        <p className="text-sm leading-relaxed break-keep text-neutral-500">
          #{setNumber} · {conditionLabel(listing.condition)} 상태로 사겠다고 걸어 둔 입찰이에요.
        </p>
      </div>
      {result !== null ? (
        <p
          role="status"
          className="rounded-lg bg-success-soft px-3 py-3 text-sm leading-relaxed break-keep text-success"
        >
          입찰가 {formatWon(result.bidPrice)}에 판매를 수락했어요. 입찰자에게 72시간 유효한 수락
          제안이 생겼어요.
          {result.bidPrice > soldAt ? ` 주문 금액은 판매가 ${formatWon(soldAt)}이에요.` : ""}
        </p>
      ) : loading ? (
        <div role="status" className="flex flex-col gap-3">
          <p className="text-sm text-neutral-500">입찰 정보를 확인하고 있어요.</p>
          <Skeleton className="h-12 w-full rounded-lg" />
        </div>
      ) : failed ? (
        <div role="alert" className="flex flex-col items-start gap-3">
          <p className="text-sm leading-relaxed break-keep text-neutral-600">
            입찰 정보를 불러오지 못했어요. 다시 확인한 뒤 판매를 진행해 주세요.
          </p>
          <Button size="sm" variant="secondary" onClick={() => setNonce((current) => current + 1)}>
            입찰 다시 확인
          </Button>
        </div>
      ) : row !== null && highest !== null ? (
        <div className="flex min-w-0 flex-col gap-3">
          <p className="flex flex-wrap items-baseline gap-x-2 gap-y-1 text-sm text-neutral-600">
            입찰 {row.bidCount}건 · 최고 입찰가{" "}
            <span className="text-xl font-bold whitespace-nowrap tabular-nums text-neutral-900">
              {formatWon(highest)}
            </span>
          </p>
          {bidAboveAsk ? (
            <>
              <p className="text-sm leading-relaxed break-keep text-neutral-600">
                최고 입찰가가 지금 판매가 {formatWon(listing.price)}보다{" "}
                {formatWon(highest - listing.price)} 높아요. 주문 금액은 판매가를 넘지 않으니,
                판매가를 입찰가로 올려 파는 편이 유리해요.
              </p>
              <Button
                size="sm"
                className={SALE_ACTION_CLASS}
                disabled={busy}
                onClick={() => void handleFill(highest)}
              >
                <span className="min-w-0 whitespace-normal">
                  {busy ? "판매 중…" : `판매가를 ${formatWon(highest)}으로 올리고 바로 판매`}
                </span>
              </Button>
              <Button
                size="sm"
                variant="secondary"
                disabled={busy}
                onClick={() => void handleFill(null)}
                className={SALE_ACTION_CLASS}
              >
                <span className="min-w-0 whitespace-normal">
                  판매가 {formatWon(listing.price)} 그대로 판매
                </span>
              </Button>
            </>
          ) : (
            <Button
              size="sm"
              className={SALE_ACTION_CLASS}
              disabled={busy}
              onClick={() => void handleFill(null)}
            >
              <span className="min-w-0 whitespace-normal">
                {busy ? "판매 중…" : `최고 입찰가 ${formatWon(highest)}에 바로 판매`}
              </span>
            </Button>
          )}
        </div>
      ) : (
        <div className="flex flex-col items-start gap-3">
          <p className="text-sm leading-relaxed break-keep text-neutral-500">
            아직 이 세트·상태에 걸린 입찰이 없어요. 입찰이 들어오면 여기서 바로 팔 수 있어요.
          </p>
          <Button size="sm" variant="secondary" onClick={() => setNonce((current) => current + 1)}>
            입찰 다시 확인
          </Button>
        </div>
      )}
      {error !== null ? (
        <p
          role="alert"
          className="rounded-lg bg-danger-soft p-3 text-sm leading-relaxed break-keep text-danger"
        >
          {error}
        </p>
      ) : null}
      {dialog.element}
    </section>
  );
}
