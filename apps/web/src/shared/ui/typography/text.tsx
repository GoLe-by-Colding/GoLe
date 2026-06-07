import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";

export type TextSize = "sm" | "md" | "lg";
export type TextTone = "default" | "secondary" | "muted";
export type TextWeight = "regular" | "medium" | "semibold";

const SIZE: Record<TextSize, string> = {
  sm: "text-sm",
  md: "text-base",
  lg: "text-lg",
};

const TONE: Record<TextTone, string> = {
  default: "text-neutral-900",
  secondary: "text-neutral-600",
  muted: "text-neutral-500",
};

const WEIGHT: Record<TextWeight, string> = {
  regular: "font-normal",
  medium: "font-medium",
  semibold: "font-semibold",
};

export interface TextProps extends HTMLAttributes<HTMLParagraphElement> {
  readonly size?: TextSize;
  readonly tone?: TextTone;
  readonly weight?: TextWeight;
  readonly children: ReactNode;
}

export function Text({
  size = "md",
  tone = "default",
  weight = "regular",
  className,
  children,
  ...rest
}: TextProps) {
  return (
    <p className={cn(SIZE[size], TONE[tone], WEIGHT[weight], className)} {...rest}>
      {children}
    </p>
  );
}
