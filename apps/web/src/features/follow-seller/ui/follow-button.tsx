"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { fetchFollowing, followSeller, unfollowSeller } from "@entities/discovery";
import { useSession } from "@entities/user";
import { loginHrefForCurrentPage } from "@shared/lib";
import { Button } from "@shared/ui";

export interface FollowButtonProps {
  readonly sellerId: string;
}

export function FollowButton({ sellerId }: FollowButtonProps) {
  const router = useRouter();
  const { session } = useSession();
  const accountId = session?.accountId ?? null;
  const followKey = accountId === null ? null : JSON.stringify([accountId, sellerId] as const);
  const [following, setFollowing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);
  const [reloadKey, setReloadKey] = useState(0);
  const checking = accountId !== null && accountId !== sellerId && loadedKey !== followKey;

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      if (accountId === null || accountId === sellerId) {
        setFollowing(false);
        setLoading(false);
        setLoadedKey(null);
        setError(undefined);
        return;
      }

      setLoading(true);
      setError(undefined);
      void fetchFollowing(accountId, controller.signal)
        .then((sellers) => {
          if (!controller.signal.aborted) {
            setFollowing(sellers.includes(sellerId));
            setLoadedKey(followKey);
          }
        })
        .catch(() => {
          if (!controller.signal.aborted) {
            setLoadedKey(followKey);
            setError("팔로우 상태를 확인하지 못했습니다.");
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
  }, [accountId, followKey, reloadKey, sellerId]);

  async function toggle() {
    if (accountId === null) {
      router.push(loginHrefForCurrentPage());
      return;
    }
    if (accountId === sellerId || busy || loading || checking || error !== undefined) return;

    setBusy(true);
    setError(undefined);
    try {
      if (following) {
        await unfollowSeller(accountId, sellerId);
        setFollowing(false);
      } else {
        await followSeller(accountId, sellerId);
        setFollowing(true);
      }
    } catch {
      setError("팔로우를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  }

  if (accountId === sellerId) {
    return null;
  }

  return (
    <div className="flex flex-col items-end gap-1.5">
      <Button
        variant={following ? "secondary" : "primary"}
        disabled={busy || loading || checking || error !== undefined}
        onClick={toggle}
      >
        {loading || checking ? "확인 중" : busy ? "처리 중" : following ? "팔로잉" : "팔로우"}
      </Button>
      {error ? (
        <div className="flex flex-col items-end gap-1">
          <span className="max-w-56 text-right text-xs text-danger" role="alert">
            {error}
          </span>
          <button
            type="button"
            className="text-xs font-semibold text-brand-700 hover:underline"
            onClick={() => {
              setLoadedKey(null);
              setReloadKey((current) => current + 1);
            }}
          >
            다시 확인
          </button>
        </div>
      ) : null}
    </div>
  );
}
