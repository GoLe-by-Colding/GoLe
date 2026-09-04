"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  addWishlist,
  fetchWishlist,
  removeWishlist,
  type WishlistTargetType,
} from "@entities/discovery";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { loginHrefForCurrentPage } from "@shared/lib";
import { Button, HeartIcon } from "@shared/ui";

export interface WishlistButtonProps {
  readonly targetType: WishlistTargetType;
  readonly targetId: string;
}

export function WishlistButton({ targetType, targetId }: WishlistButtonProps) {
  const router = useRouter();
  const { session } = useSession();
  const accountId = session?.accountId ?? null;
  const wishlistKey =
    accountId === null ? null : JSON.stringify([accountId, targetType, targetId] as const);
  const [saved, setSaved] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);
  const checking = accountId !== null && loadedKey !== wishlistKey;

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      if (accountId === null) {
        setSaved(false);
        setLoading(false);
        setLoadedKey(null);
        setError(undefined);
        return;
      }

      setLoading(true);
      setError(undefined);
      void fetchWishlist(accountId, controller.signal)
        .then((entries) => {
          if (!controller.signal.aborted) {
            setSaved(
              entries.some(
                (entry) => entry.targetType === targetType && entry.targetId === targetId,
              ),
            );
            setLoadedKey(wishlistKey);
          }
        })
        .catch((cause: unknown) => {
          if (!controller.signal.aborted) {
            setLoadedKey(wishlistKey);
            setError(
              cause instanceof ApiError ? cause.message : "위시리스트 상태를 불러오지 못했습니다.",
            );
          }
        })
        .finally(() => {
          if (!controller.signal.aborted) {
            setLoading(false);
          }
        });
    }, 0);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [accountId, targetId, targetType, wishlistKey]);

  async function handleToggle() {
    if (accountId === null) {
      router.push(loginHrefForCurrentPage());
      return;
    }
    if (busy || loading || checking) {
      return;
    }
    const previous = saved;
    setSaved(!previous);
    setBusy(true);
    setError(undefined);
    try {
      if (previous) {
        await removeWishlist(accountId, targetType, targetId);
      } else {
        await addWishlist(accountId, targetType, targetId);
      }
    } catch (cause) {
      if (!previous && cause instanceof ApiError && cause.status === 409) {
        setSaved(true);
      } else if (previous && cause instanceof ApiError && cause.status === 404) {
        setSaved(false);
      } else {
        setSaved(previous);
        setError("위시리스트를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col items-start gap-1.5">
      <Button
        variant="secondary"
        disabled={busy || loading || checking}
        aria-pressed={saved}
        onClick={handleToggle}
      >
        <HeartIcon className="h-4 w-4" filled={saved} />
        {loading || checking
          ? "위시 확인 중"
          : busy
            ? "처리 중"
            : saved
              ? "위시 빼기"
              : "위시 담기"}
      </Button>
      {error ? (
        <span className="text-sm text-danger" role="alert">
          {error}
        </span>
      ) : null}
    </div>
  );
}
