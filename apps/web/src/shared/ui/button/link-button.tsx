import type { ReactNode } from "react";
import Link from "next/link";
import { cn } from "@shared/lib";
import {
  BUTTON_BASE,
  BUTTON_SIZE,
  BUTTON_VARIANT,
  type ButtonSize,
  type ButtonVariant,
} from "./button";

export interface LinkButtonProps {
  readonly href: string;
  readonly variant?: ButtonVariant;
  readonly size?: ButtonSize;
  readonly fullWidth?: boolean;
  readonly className?: string | undefined;
  readonly children: ReactNode;
}

/**
 * 버튼 외형을 가진 링크. Button과 동일한 Tailwind 클래스 셋을 재사용한다.
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
        BUTTON_BASE,
        BUTTON_VARIANT[variant],
        BUTTON_SIZE[size],
        fullWidth && "w-full",
        className,
      )}
    >
      {children}
    </Link>
  );
}
