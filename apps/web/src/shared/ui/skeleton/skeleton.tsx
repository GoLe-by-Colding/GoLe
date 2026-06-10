import type { HTMLAttributes } from "react";
import { cn } from "@shared/lib";

export interface SkeletonProps extends HTMLAttributes<HTMLDivElement> {
  /** 원형 스켈레톤(아바타 등). 기본 false → rounded-md. */
  readonly circle?: boolean;
}

/**
 * 로딩 자리표시자. 콘텐츠 도착 전 레이아웃 안정화를 위한 펄스 블록.
 * 크기는 호출부에서 className(h-*, w-*)으로 지정한다.
 */
export function Skeleton({ circle = false, className, ...rest }: SkeletonProps) {
  return (
    <div
      aria-hidden="true"
      className={cn(
        "animate-pulse bg-neutral-200/70",
        circle ? "rounded-full" : "rounded-md",
        className,
      )}
      {...rest}
    />
  );
}
