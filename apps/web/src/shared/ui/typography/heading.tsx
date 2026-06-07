import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";
import styles from "./typography.module.css";

export type HeadingLevel = 1 | 2 | 3;

export interface HeadingProps extends HTMLAttributes<HTMLHeadingElement> {
  readonly level?: HeadingLevel;
  readonly children: ReactNode;
}

const LEVEL_CLASS: Record<HeadingLevel, string | undefined> = {
  1: styles.h1,
  2: styles.h2,
  3: styles.h3,
};

export function Heading({ level = 2, className, children, ...rest }: HeadingProps) {
  const Tag = `h${level}` as const;
  return (
    <Tag className={cn(LEVEL_CLASS[level], className)} {...rest}>
      {children}
    </Tag>
  );
}
