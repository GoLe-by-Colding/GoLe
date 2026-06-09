import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";

export type ButtonVariant = "primary" | "secondary" | "ghost";
export type ButtonSize = "sm" | "md" | "lg";

export const BUTTON_BASE =
  "inline-flex items-center justify-center gap-2 rounded-xl font-semibold leading-none whitespace-nowrap transition-all duration-200 ease-out active:scale-[0.97] disabled:opacity-40 disabled:cursor-not-allowed disabled:active:scale-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-400";

export const BUTTON_VARIANT: Record<ButtonVariant, string> = {
  primary: "bg-brand-500 text-white shadow-[0_1px_3px_rgba(47,86,230,0.3)] hover:bg-brand-600 hover:shadow-[0_3px_12px_rgba(47,86,230,0.25)]",
  secondary:
    "bg-white text-neutral-900 border border-neutral-200/80 shadow-soft hover:bg-neutral-50 hover:border-neutral-300 hover:shadow-lift",
  ghost: "bg-transparent text-neutral-600 hover:bg-neutral-100 hover:text-neutral-900",
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
