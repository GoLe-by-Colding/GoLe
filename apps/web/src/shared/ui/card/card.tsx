import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";

export type CardElevation = "flat" | "raised";

const ELEVATION: Record<CardElevation, string> = {
  flat: "shadow-none",
  raised: "shadow-sm",
};

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
        "bg-white border border-neutral-200 rounded-lg overflow-hidden",
        ELEVATION[elevation],
        interactive &&
          "cursor-pointer transition hover:shadow-md hover:-translate-y-0.5 hover:border-neutral-300",
        padded && "p-5",
        className,
      )}
      {...rest}
    >
      {children}
    </div>
  );
}
