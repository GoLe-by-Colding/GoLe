import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";

export type HeadingLevel = 1 | 2 | 3;

const LEVEL: Record<HeadingLevel, string> = {
  1: "text-4xl font-bold leading-tight tracking-tight max-sm:text-3xl",
  2: "text-3xl font-bold leading-tight tracking-tight",
  3: "text-xl font-semibold leading-tight",
};

export interface HeadingProps extends HTMLAttributes<HTMLHeadingElement> {
  readonly level?: HeadingLevel;
  /** 보이는 크기. 문서 구조(level)와 다를 때만 준다 — 예: 카드 안 페이지 제목은 h1이지만 h2 크기. */
  readonly size?: HeadingLevel;
  readonly children: ReactNode;
}

export function Heading({ level = 2, size, className, children, ...rest }: HeadingProps) {
  const Tag = `h${level}` as const;
  return (
    <Tag className={cn(LEVEL[size ?? level], className)} {...rest}>
      {children}
    </Tag>
  );
}
