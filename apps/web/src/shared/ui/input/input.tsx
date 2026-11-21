import type { InputHTMLAttributes } from "react";
import { cn } from "@shared/lib";
import { FORM_CONTROL_BASE, formControlState } from "../form-control";

export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "size"> {
  readonly invalid?: boolean;
  /** sm: 목록·대화 안의 보조 입력(40px), md: 폼 기본(44px). */
  readonly size?: "sm" | "md";
}

const INPUT_SIZE = { sm: "h-10 text-sm", md: "h-11" } as const;

export function Input({
  invalid = false,
  size = "md",
  className,
  type = "text",
  ...rest
}: InputProps) {
  return (
    <input
      type={type}
      aria-invalid={invalid}
      className={cn(FORM_CONTROL_BASE, INPUT_SIZE[size], formControlState(invalid), className)}
      {...rest}
    />
  );
}
