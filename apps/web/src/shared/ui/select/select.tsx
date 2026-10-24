import type { SelectHTMLAttributes } from "react";
import { cn } from "@shared/lib";
import { FORM_CONTROL_BASE, formControlState } from "../form-control";

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  readonly invalid?: boolean;
}

export function Select({ invalid = false, className, children, ...rest }: SelectProps) {
  return (
    <select
      aria-invalid={invalid}
      className={cn(FORM_CONTROL_BASE, "h-11", formControlState(invalid), className)}
      {...rest}
    >
      {children}
    </select>
  );
}
