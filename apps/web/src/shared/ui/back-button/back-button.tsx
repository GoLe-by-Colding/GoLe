"use client";

import { useRouter } from "next/navigation";
import { cn } from "@shared/lib";

export interface BackButtonProps {
  /** 히스토리가 없을 때 이동할 폴백 경로. 기본 "/". */
  readonly fallbackHref?: string;
  readonly label?: string;
  readonly className?: string;
}

/**
 * 뒤로가기 버튼. 브라우저 히스토리가 있으면 router.back(), 없으면(직접 진입) 폴백 경로로 이동.
 */
export function BackButton({ fallbackHref = "/", label = "뒤로", className }: BackButtonProps) {
  const router = useRouter();

  function handleClick() {
    if (typeof window !== "undefined" && window.history.length > 1) {
      router.back();
    } else {
      router.push(fallbackHref);
    }
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      aria-label="뒤로 가기"
      className={cn(
        "inline-flex items-center gap-1 self-start rounded-lg px-2 py-1 text-sm font-medium text-neutral-500 transition-colors hover:bg-neutral-100 hover:text-neutral-900",
        className,
      )}
    >
      <span aria-hidden="true">←</span>
      {label}
    </button>
  );
}
