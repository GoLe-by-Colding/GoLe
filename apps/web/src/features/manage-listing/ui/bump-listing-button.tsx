"use client";

import { useState } from "react";
import {
  bumpCooldownRemainingMs,
  bumpListing,
  formatCooldown,
  LISTING_ERROR_CODES,
  listingMutationErrorMessage,
  type Listing,
} from "@entities/listing";
import { ApiError } from "@shared/api";
import { Button, type ButtonSize } from "@shared/ui";
import { useClock } from "../model/use-clock";

export interface BumpListingButtonProps {
  readonly listing: Pick<Listing, "id" | "bumpAvailableAt">;
  readonly size?: ButtonSize;
  /** 끌올 성공 후 서버가 돌려준 매물. 목록 순서를 바꾸는 등 호출부가 후속 처리를 한다. */
  readonly onBumped?: (listing: Listing) => void;
}

/**
 * 끌올 버튼. 쿨다운 안이면 남은 시간을 보여 주고 버튼을 잠근다.
 *
 * <p>쿨다운 판정의 정본은 서버다. 서버가 `bumpAvailableAt`을 주지 않으면(구 API) 버튼을 열어
 * 두고, 429가 오면 `Retry-After`로 남은 시간을 채운다.
 */
export function BumpListingButton({ listing, size = "sm", onBumped }: BumpListingButtonProps) {
  const now = useClock();
  // 끌올 성공·429 응답으로 알게 된 다음 가능 시각. 없으면 서버가 처음 준 값을 쓴다.
  const [availableAtOverride, setAvailableAtOverride] = useState<string | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const availableAt = availableAtOverride ?? listing.bumpAvailableAt;
  const remainingMs =
    now === null || availableAt === undefined
      ? 0
      : bumpCooldownRemainingMs({ bumpAvailableAt: availableAt }, now);
  const coolingDown = remainingMs > 0;

  async function handleBump() {
    if (busy || coolingDown) return;
    setBusy(true);
    setNotice(null);
    setError(null);
    try {
      const updated = await bumpListing(listing.id);
      if (updated.bumpAvailableAt !== undefined) {
        setAvailableAtOverride(updated.bumpAvailableAt);
      }
      setNotice("끌올했어요. 최신순 맨 앞에 다시 보여요.");
      onBumped?.(updated);
    } catch (cause) {
      if (
        cause instanceof ApiError &&
        cause.code === LISTING_ERROR_CODES.bumpCooldown &&
        cause.retryAfterMs !== null
      ) {
        setAvailableAtOverride(new Date(Date.now() + cause.retryAfterMs).toISOString());
      }
      setError(listingMutationErrorMessage(cause, "bump"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <Button
        variant="secondary"
        size={size}
        disabled={busy || coolingDown}
        onClick={() => void handleBump()}
      >
        {busy ? "끌올 중…" : "끌올"}
      </Button>
      {coolingDown && error === null ? (
        <span className="text-xs text-neutral-500">{formatCooldown(remainingMs)} 후 끌올 가능</span>
      ) : null}
      {notice !== null ? (
        <span role="status" className="text-xs text-success">
          {notice}
        </span>
      ) : null}
      {error !== null ? (
        <span role="alert" className="text-xs text-danger">
          {error}
        </span>
      ) : null}
    </div>
  );
}
