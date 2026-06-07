import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";
import styles from "./card.module.css";

export type CardElevation = "flat" | "raised";

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  readonly elevation?: CardElevation;
  readonly interactive?: boolean;
  readonly padded?: boolean;
  readonly children: ReactNode;
}

export function Card({
  elevation = "raised",
  interactive = false,
  padded = false,
  className,
  children,
  ...rest
}: CardProps) {
  return (
    <div
      className={cn(
        styles.card,
        styles[elevation],
        interactive && styles.interactive,
        padded && styles.padded,
        className,
      )}
      {...rest}
    >
      {children}
    </div>
  );
}
