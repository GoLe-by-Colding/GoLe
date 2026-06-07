import type { ReactNode } from "react";
import Link from "next/link";
import { cn } from "@shared/lib";
import styles from "./button.module.css";
import type { ButtonSize, ButtonVariant } from "./button";

export interface LinkButtonProps {
  readonly href: string;
  readonly variant?: ButtonVariant;
  readonly size?: ButtonSize;
  readonly fullWidth?: boolean;
  readonly className?: string | undefined;
  readonly children: ReactNode;
}

/**
 * 버튼 외형을 가진 링크. 시각적으로 Button과 동일한 토큰/클래스를 사용한다.
 */
export function LinkButton({
  href,
  variant = "primary",
  size = "md",
  fullWidth = false,
  className,
  children,
}: LinkButtonProps) {
  return (
    <Link
      href={href}
      className={cn(
        styles.button,
        styles[variant],
        styles[size],
        fullWidth && styles.fullWidth,
        className,
      )}
    >
      {children}
    </Link>
  );
}
