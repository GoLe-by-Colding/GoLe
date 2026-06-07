import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";
import styles from "./typography.module.css";

export type TextSize = "sm" | "md" | "lg";
export type TextTone = "default" | "secondary" | "muted";
export type TextWeight = "regular" | "medium" | "semibold";

export interface TextProps extends HTMLAttributes<HTMLParagraphElement> {
  readonly size?: TextSize;
  readonly tone?: TextTone;
  readonly weight?: TextWeight;
  readonly children: ReactNode;
}

const SIZE_CLASS: Record<TextSize, string | undefined> = {
  sm: styles.textSm,
  md: styles.textMd,
  lg: styles.textLg,
};

const TONE_CLASS: Record<TextTone, string | undefined> = {
  default: styles.default,
  secondary: styles.secondary,
  muted: styles.muted,
};

const WEIGHT_CLASS: Record<TextWeight, string | undefined> = {
  regular: undefined,
  medium: styles.weightMedium,
  semibold: styles.weightSemibold,
};

export function Text({
  size = "md",
  tone = "default",
  weight = "regular",
  className,
  children,
  ...rest
}: TextProps) {
  return (
    <p
      className={cn(SIZE_CLASS[size], TONE_CLASS[tone], WEIGHT_CLASS[weight], className)}
      {...rest}
    >
      {children}
    </p>
  );
}
