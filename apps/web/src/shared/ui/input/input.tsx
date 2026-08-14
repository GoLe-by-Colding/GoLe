import type { InputHTMLAttributes } from "react";
import { cn } from "@shared/lib";

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  readonly invalid?: boolean;
}

export function Input({ invalid = false, className, type = "text", ...rest }: InputProps) {
  return (
    <input
      type={type}
      aria-invalid={invalid}
      className={cn(
        "w-full h-11 px-3 rounded-md bg-white text-neutral-900 text-base border transition-colors",
        "placeholder:text-neutral-500 hover:border-neutral-400",
        "focus:outline-none focus:ring-2",
        invalid
          ? "border-danger focus:border-danger focus:ring-danger-soft"
          : "border-neutral-300 focus:border-brand-500 focus:ring-brand-50",
        "disabled:bg-neutral-100 disabled:opacity-70 disabled:cursor-not-allowed",
        className,
      )}
      {...rest}
    />
  );
}
