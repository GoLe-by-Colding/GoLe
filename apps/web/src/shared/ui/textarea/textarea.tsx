import type { TextareaHTMLAttributes } from "react";
import { cn } from "@shared/lib";

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  readonly invalid?: boolean;
}

export function Textarea({ invalid = false, className, rows = 4, ...rest }: TextareaProps) {
  return (
    <textarea
      rows={rows}
      aria-invalid={invalid}
      className={cn(
        "w-full px-3 py-2 rounded-md bg-white text-neutral-900 text-base border transition-colors resize-y",
        "placeholder:text-neutral-500 hover:border-neutral-400 focus:outline-none focus:ring-2",
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
