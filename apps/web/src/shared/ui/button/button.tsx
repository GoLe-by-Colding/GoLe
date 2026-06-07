import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";
import styles from "./button.module.css";

export type ButtonVariant = "primary" | "secondary" | "ghost";
export type ButtonSize = "sm" | "md" | "lg";

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
        styles.button,
        styles[variant],
        styles[size],
        fullWidth && styles.fullWidth,
        className,
      )}
      {...rest}
    >
      {children}
    </button>
  );
}
