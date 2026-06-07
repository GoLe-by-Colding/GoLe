import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";
import styles from "./badge.module.css";

export type BadgeTone = "neutral" | "brand" | "success" | "danger" | "warning";

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  readonly tone?: BadgeTone;
  readonly children: ReactNode;
}

export function Badge({ tone = "neutral", className, children, ...rest }: BadgeProps) {
  return (
    <span className={cn(styles.badge, styles[tone], className)} {...rest}>
      {children}
    </span>
  );
}
