"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { likePost } from "@entities/community";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { cn } from "@shared/lib";

export interface LikeButtonProps {
  readonly postId: string;
  readonly initialLikeCount: number;
}

export function LikeButton({ postId, initialLikeCount }: LikeButtonProps) {
  const router = useRouter();
  const { session } = useSession();
  const [count, setCount] = useState(initialLikeCount);
  const [liked, setLiked] = useState(false);
  const [busy, setBusy] = useState(false);

  async function handleLike() {
    if (!session) {
      router.push("/login");
      return;
    }
    if (liked || busy) {
      return;
    }
    setBusy(true);
    try {
      await likePost(postId, session.accountId);
      setLiked(true);
      setCount((c) => c + 1);
    } catch (cause) {
      // 이미 좋아요한 경우(409)는 좋아요 상태로 처리
      if (cause instanceof ApiError && cause.status === 409) {
        setLiked(true);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <button
      type="button"
      onClick={handleLike}
      aria-pressed={liked}
      className={cn(
        "inline-flex items-center gap-1 text-sm font-medium transition-colors",
        liked ? "text-brand-500" : "text-neutral-500 hover:text-neutral-900",
      )}
    >
      <span aria-hidden="true">{liked ? "♥" : "♡"}</span>
      {count}
    </button>
  );
}
