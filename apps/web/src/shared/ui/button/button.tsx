import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";

export type ButtonVariant = "primary" | "secondary" | "ghost" | "accent" | "inverse" | "danger";
export type ButtonSize = "sm" | "md" | "lg";

export const BUTTON_BASE =
  "inline-flex items-center justify-center gap-2 rounded-lg font-semibold leading-none whitespace-nowrap transition-press disabled:cursor-not-allowed disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-400";

export const BUTTON_VARIANT: Record<ButtonVariant, string> = {
  /* 면이 있는 버튼은 아래 턱(brick)이 눌리며 내려앉는다. 턱 색은 면보다 두 단계 진하게 둔다. */
  primary: "brick bg-brand-600 text-white [--brick-edge:var(--color-brand-800)] hover:bg-brand-700",
  secondary:
    "brick border border-neutral-300 bg-white text-neutral-900 [--brick-edge:var(--color-neutral-300)] hover:bg-neutral-50",
  /* 면이 없는 버튼은 턱 대신 살짝 작아지는 누름을 쓴다. */
  ghost:
    "bg-transparent text-neutral-600 transition-press motion-safe:not-disabled:active:scale-96 hover:bg-neutral-100 hover:text-neutral-900",
  /* 브릭 골드 — 핵심 CTA 전용 (판매하기, 다크 히어로 위) */
  accent:
    "brick bg-accent-400 text-neutral-900 [--brick-edge:var(--color-accent-600)] hover:bg-accent-500",
  /* 딥 오션(다크) 배경 위 보조 버튼 */
  inverse:
    "border border-white/50 bg-transparent text-white transition-press motion-safe:not-disabled:active:scale-96 hover:border-white hover:bg-white/10",
  /* 파괴적 조치 — 매물 내림·게시글 삭제·계정 정지 등 되돌리기 어려운 운영 액션 전용 */
  danger:
    "brick bg-danger text-white [--brick-edge:color-mix(in_oklab,var(--color-danger)_70%,black)] hover:brightness-110",
};

export const BUTTON_SIZE: Record<ButtonSize, string> = {
  sm: "h-11 px-3 text-sm sm:h-9 [@media(pointer:coarse)]:min-h-11",
  md: "h-11 px-5 text-base",
  lg: "h-13 px-6 text-lg",
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  readonly variant?: ButtonVariant;
  readonly size?: ButtonSize;
  readonly fullWidth?: boolean;
  readonly loading?: boolean;
  readonly loadingLabel?: string;
  readonly children: ReactNode;
}

export function Button({
  variant = "primary",
  size = "md",
  fullWidth = false,
  loading = false,
  loadingLabel,
  disabled,
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
      disabled={disabled || loading}
      aria-busy={loading || rest["aria-busy"]}
    >
      {loading && loadingLabel ? loadingLabel : children}
    </button>
  );
}
