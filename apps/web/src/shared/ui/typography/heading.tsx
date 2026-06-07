import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";

export type HeadingLevel = 1 | 2 | 3;

const LEVEL: Record<HeadingLevel, string> = {
  1: "text-5xl font-bold leading-tight tracking-tight max-sm:text-4xl",
  2: "text-3xl font-bold leading-tight tracking-tight",
  3: "text-xl font-semibold leading-tight",
};

export interface HeadingProps extends HTMLAttributes<HTMLHeadingElement> {
  readonly level?: HeadingLevel;
  readonly children: ReactNode;
}

export function Heading({ level = 2, className, children, ...rest }: HeadingProps) {
  const Tag = `h${level}` as const;
  return (
    <Tag className={cn(LEVEL[level], className)} {...rest}>
      {children}
    </Tag>
  );
}
