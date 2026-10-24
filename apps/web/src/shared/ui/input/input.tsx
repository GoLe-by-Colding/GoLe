import type { InputHTMLAttributes } from "react";
import { cn } from "@shared/lib";
import { FORM_CONTROL_BASE, formControlState } from "../form-control";

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  readonly invalid?: boolean;
}

export function Input({ invalid = false, className, type = "text", ...rest }: InputProps) {
  return (
    <input
      type={type}
      aria-invalid={invalid}
      className={cn(FORM_CONTROL_BASE, "h-11", formControlState(invalid), className)}
      {...rest}
    />
  );
}
