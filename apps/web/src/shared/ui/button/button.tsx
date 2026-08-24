import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";

export type ButtonVariant = "primary" | "secondary" | "ghost" | "accent" | "inverse" | "danger";
export type ButtonSize = "sm" | "md" | "lg";

export const BUTTON_BASE =
  "inline-flex items-center justify-center gap-2 rounded-md font-semibold leading-none whitespace-nowrap transition-[color,background-color,border-color,opacity,transform,box-shadow] duration-200 ease-out motion-safe:hover:-translate-y-0.5 motion-safe:active:translate-y-0 motion-safe:active:scale-[0.98] motion-reduce:transform-none disabled:cursor-not-allowed disabled:opacity-40 disabled:transform-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-400";

export const BUTTON_VARIANT: Record<ButtonVariant, string> = {
  primary: "bg-brand-600 text-white hover:bg-brand-700",
  secondary:
    "border border-neutral-300 bg-white text-neutral-900 hover:border-neutral-400 hover:bg-neutral-50",
  ghost: "bg-transparent text-neutral-600 hover:bg-neutral-100 hover:text-neutral-900",
  /* 브릭 골드 — 핵심 CTA 전용 (판매하기, 다크 히어로 위) */
  accent: "bg-accent-400 text-neutral-900 hover:bg-accent-500",
  /* 딥 오션(다크) 배경 위 보조 버튼 */
  inverse: "border border-white/50 bg-transparent text-white hover:border-white hover:bg-white/10",
  /* 파괴적 조치 — 매물 내림·게시글 삭제·계정 정지 등 되돌리기 어려운 운영 액션 전용 */
  danger: "bg-danger text-white hover:brightness-110",
};

export const BUTTON_SIZE: Record<ButtonSize, string> = {
  sm: "h-9 px-3 text-sm",
  md: "h-11 px-5 text-base",
  lg: "h-13 px-6 text-lg",
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  readonly variant?: ButtonVariant;
  readonly size?: ButtonSize;
  readonly fullWidth?: boolean;
  readonly children: ReactNode;
}

export function Button({
  variant = "primary",
  size = "md",
  fullWidth = false,
  type = "button",
  className,
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={cn(
        BUTTON_BASE,
        BUTTON_VARIANT[variant],
        BUTTON_SIZE[size],
        fullWidth && "w-full",
        className,
      )}
      {...rest}
    >
      {children}
    </button>
  );
}
