"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { bidErrorMessage, BidStatusBadge, cancelBid, fetchMyBids, type Bid } from "@entities/bid";
import { conditionLabel, formatWon } from "@entities/listing";
import { cn } from "@shared/lib";
import {
  AlertCircleIcon,
  Button,
  EmptyState,
  LinkButton,
  Skeleton,
  TrendingUpIcon,
} from "@shared/ui";

type Load =
  | { readonly status: "loading" }
  | { readonly status: "ready"; readonly bids: readonly Bid[] }
  | { readonly status: "failed" };

function formatDate(iso: string): string {
  const at = new Date(iso);
  return Number.isNaN(at.getTime())
    ? "—"
    : `${at.getFullYear()}.${String(at.getMonth() + 1).padStart(2, "0")}.${String(at.getDate()).padStart(2, "0")}`;
}

function bidTimeline(bid: Bid): string {
  switch (bid.status) {
    case "active":
      return `${formatDate(bid.expiresAt)}까지 · ${bid.durationDays}일`;
    case "filled":
      return "판매자가 이 가격에 판매를 수락했어요";
    case "expired":
      return `${formatDate(bid.expiresAt)}에 만료됐어요`;
    case "canceled":
      return "취소한 입찰이에요";
  }
}

/**
 * 프로필 "입찰" 탭 — 내 구매 입찰 목록. (buy-bids F1)
 *
 * 진행 중이면 취소하고, 체결됐으면 매물로 간다(그 매물에 내 수락 제안이 생겨 있다). 만료·취소는 흐리게 둔다.
 * 탭을 열 때 처음 읽는다 — 프로필 첫 화면에서 쓰지 않는 요청을 미리 보내지 않는다.
 */
export function MyBidsPanel() {
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [cancelling, setCancelling] = useState<string | null>(null);
  const [rowError, setRowError] = useState<{ readonly bidId: string; readonly message: string }>();

  useEffect(() => {
    const controller = new AbortController();
    fetchMyBids(controller.signal).then(
      (bids) => {
        if (!controller.signal.aborted) setLoad({ status: "ready", bids });
      },
      () => {
        if (!controller.signal.aborted) setLoad({ status: "failed" });
      },
    );
    return () => controller.abort();
  }, [attempt]);

  function retry() {
    setLoad({ status: "loading" });
    setAttempt((current) => current + 1);
  }

  async function handleCancel(bid: Bid) {
    if (cancelling !== null) return;
    setCancelling(bid.id);
    setRowError(undefined);
    try {
      await cancelBid(bid.id);
      setLoad((current) =>
        current.status === "ready"
          ? {
              status: "ready",
              bids: current.bids.map((item) =>
                item.id === bid.id ? { ...item, status: "canceled" } : item,
              ),
            }
          : current,
      );
    } catch (cause) {
      setRowError({ bidId: bid.id, message: bidErrorMessage(cause, "cancel") });
    } finally {
      setCancelling(null);
    }
  }

  if (load.status === "loading") {
    return (
      <div className="flex flex-col gap-3">
        {[1, 2].map((i) => (
          <Skeleton key={i} className="h-16 w-full rounded-lg" />
        ))}
      </div>
    );
  }

  if (load.status === "failed") {
    return (
      <EmptyState
        variant="inline"
        icon={<AlertCircleIcon className="h-8 w-8 text-neutral-400" strokeWidth={1.5} />}
        title="입찰을 불러오지 못했어요"
        action={
          <Button variant="ghost" size="sm" onClick={retry}>
            다시 시도
          </Button>
        }
      />
    );
  }

  if (load.bids.length === 0) {
    return (
      <EmptyState
        variant="inline"
        icon={<TrendingUpIcon className="h-8 w-8 text-neutral-400" strokeWidth={1.5} />}
        title="걸어 둔 입찰이 없어요"
        description="세트 상세의 '구매 입찰'에서 원하는 상태와 가격으로 입찰을 걸 수 있어요."
        action={
          <LinkButton href="/prices" size="sm">
            시세에서 세트 찾기
          </LinkButton>
        }
      />
    );
  }

  return (
    <ul className="flex flex-col gap-3" aria-label="내 입찰">
      {load.bids.map((bid) => {
        const closed = bid.status === "expired" || bid.status === "canceled";
        const error = rowError?.bidId === bid.id ? rowError.message : null;
        return (
          <li
            key={bid.id}
            data-testid="my-bid"
            className={cn(
              "flex flex-col gap-2 rounded-lg border border-neutral-200 bg-white px-4 py-3.5",
              closed && "opacity-60",
            )}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex min-w-0 flex-col gap-0.5">
                <Link
                  href={`/sets/${encodeURIComponent(bid.setNumber)}`}
                  className="truncate text-sm font-medium text-neutral-900 hover:text-brand-700 hover:underline"
                >
                  #{bid.setNumber} · {conditionLabel(bid.condition)}
                </Link>
                <span className="text-base font-semibold tabular-nums text-neutral-900">
                  {formatWon(bid.price)}
                </span>
                <span className="text-xs text-neutral-500">{bidTimeline(bid)}</span>
              </div>
              <BidStatusBadge status={bid.status} />
            </div>
            {bid.status === "active" ? (
              <div className="flex justify-end">
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={cancelling !== null}
                  onClick={() => void handleCancel(bid)}
                >
                  {cancelling === bid.id ? "취소 중…" : "입찰 취소"}
                </Button>
              </div>
            ) : null}
            {bid.status === "filled" && bid.filledListingId !== null ? (
              <div className="flex justify-end">
                <LinkButton
                  href={`/listings/${encodeURIComponent(bid.filledListingId)}`}
                  size="sm"
                  variant="secondary"
                >
                  매물 보기
                </LinkButton>
              </div>
            ) : null}
            {error !== null ? (
              <p role="alert" className="text-sm text-danger">
                {error}
              </p>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}
