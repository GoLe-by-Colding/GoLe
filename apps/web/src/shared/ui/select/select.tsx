import type { SelectHTMLAttributes } from "react";
import { cn } from "@shared/lib";

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  readonly invalid?: boolean;
}

export function Select({ invalid = false, className, children, ...rest }: SelectProps) {
  return (
    <select
      aria-invalid={invalid}
      className={cn(
        "w-full h-11 px-3 rounded-md bg-white text-neutral-900 text-base border transition",
        "hover:border-neutral-400 focus:outline-none focus:ring-2",
        invalid
          ? "border-danger focus:border-danger focus:ring-danger-soft"
          : "border-neutral-300 focus:border-brand-500 focus:ring-brand-50",
        "disabled:bg-neutral-100 disabled:opacity-70 disabled:cursor-not-allowed",
        className,
      )}
      {...rest}
    >
      {children}
    </select>
  );
}
