"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { likePost, unlikePost } from "@entities/community";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { cn, loginHrefForCurrentPage } from "@shared/lib";
import { HeartIcon } from "@shared/ui";

export interface LikeButtonProps {
  readonly postId: string;
  readonly initialLikeCount: number;
  readonly initialLiked?: boolean;
}

export function LikeButton({ postId, initialLikeCount, initialLiked = false }: LikeButtonProps) {
  const router = useRouter();
  const { session } = useSession();
  const [count, setCount] = useState(initialLikeCount);
  const [liked, setLiked] = useState(initialLiked);
  const [busy, setBusy] = useState(false);
  // 사용자가 좋아요를 누른 횟수. 하트의 key 로 써서 누를 때마다 튀는 반응을 다시 재생한다.
  // 처음부터 좋아요인 글에서는 0 이라 튀지 않는다.
  const [pops, setPops] = useState(0);
  const [error, setError] = useState<string | undefined>(undefined);

  async function handleToggle() {
    if (!session) {
      router.push(loginHrefForCurrentPage());
      return;
    }
    if (busy) {
      return;
    }
    const previousLiked = liked;
    const previousCount = count;
    const nextLiked = !previousLiked;
    setError(undefined);
    setLiked(nextLiked);
    if (nextLiked) setPops((value) => value + 1);
    setCount(Math.max(0, previousCount + (nextLiked ? 1 : -1)));
    setBusy(true);
    try {
      if (nextLiked) {
        await likePost(postId);
      } else {
        await unlikePost(postId);
      }
    } catch (cause) {
      // 오래된 화면에서 서버에는 이미 좋아요가 있던 경우, 서버 카운트는 이전 화면 값에 이미
      // 포함돼 있으므로 낙관적으로 더한 1만 되돌리고 선택 상태는 유지한다.
      if (nextLiked && cause instanceof ApiError && cause.status === 409) {
        setLiked(true);
        setCount(previousCount);
      } else {
        setLiked(previousLiked);
        setCount(previousCount);
        setError("좋아요를 처리하지 못했어요. 잠시 후 다시 시도해 주세요.");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={handleToggle}
        disabled={busy}
        aria-pressed={liked}
        aria-label={`${liked ? "좋아요 취소" : "좋아요"}, ${count}개`}
        className={cn(
          "inline-flex items-center gap-1 text-sm font-medium transition-press motion-safe:not-disabled:active:scale-90 disabled:opacity-60",
          liked ? "text-brand-500" : "text-neutral-500 hover:text-neutral-900",
        )}
      >
        <HeartIcon
          key={pops}
          className={cn("h-4 w-4", pops > 0 && liked && "motion-safe:animate-like-pop")}
          filled={liked}
        />
        {count}
      </button>
      <span className="sr-only" role="status" aria-live="polite">
        {error ?? ""}
      </span>
    </>
  );
}
