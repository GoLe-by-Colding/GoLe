import type { TextareaHTMLAttributes } from "react";
import { cn } from "@shared/lib";
import { FORM_CONTROL_BASE, formControlState } from "../form-control";

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  readonly invalid?: boolean;
}

export function Textarea({ invalid = false, className, rows = 4, ...rest }: TextareaProps) {
  return (
    <textarea
      rows={rows}
      aria-invalid={invalid}
      className={cn(FORM_CONTROL_BASE, "resize-y py-2", formControlState(invalid), className)}
      {...rest}
    />
  );
}
