import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";

export type ButtonVariant = "primary" | "secondary" | "ghost" | "accent" | "inverse" | "danger";
export type ButtonSize = "sm" | "md" | "lg";

export const BUTTON_BASE =
  "inline-flex items-center justify-center gap-2 rounded-xl font-semibold leading-none whitespace-nowrap transition-all duration-200 ease-out active:scale-[0.97] disabled:opacity-40 disabled:cursor-not-allowed disabled:active:scale-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-400";

export const BUTTON_VARIANT: Record<ButtonVariant, string> = {
  primary:
    "bg-gradient-to-b from-brand-500 to-brand-700 text-white shadow-brand hover:from-brand-600 hover:to-brand-800 hover:shadow-[0_6px_20px_rgba(29,78,216,0.4)] hover:-translate-y-px",
  secondary:
    "bg-white text-neutral-900 border border-neutral-200/80 shadow-soft hover:bg-neutral-50 hover:border-neutral-300 hover:shadow-md",
  ghost: "bg-transparent text-neutral-600 hover:bg-neutral-100 hover:text-neutral-900",
  /* 브릭 골드 — 핵심 CTA 전용 (판매하기, 다크 히어로 위) */
  accent:
    "bg-gradient-to-b from-accent-300 to-accent-400 text-neutral-900 shadow-[0_4px_14px_-3px_rgba(234,179,8,0.5)] hover:from-accent-200 hover:to-accent-300 hover:shadow-[0_6px_20px_-3px_rgba(234,179,8,0.6)] hover:-translate-y-px",
  /* 딥 오션(다크) 배경 위 보조 버튼 */
  inverse:
    "bg-white/10 text-white border border-white/20 backdrop-blur-sm hover:bg-white/15 hover:border-white/30",
  /* 파괴적 조치 — 매물 내림·게시글 삭제·계정 정지 등 되돌리기 어려운 운영 액션 전용 */
  danger:
    "bg-danger text-white shadow-[0_4px_14px_-3px_rgba(220,38,38,0.45)] hover:brightness-110 hover:-translate-y-px",
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
