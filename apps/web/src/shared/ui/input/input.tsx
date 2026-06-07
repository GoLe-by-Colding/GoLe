import type { InputHTMLAttributes } from "react";
import { cn } from "@shared/lib";
import styles from "./input.module.css";

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  readonly invalid?: boolean;
}

export function Input({ invalid = false, className, type = "text", ...rest }: InputProps) {
  return (
    <input
      type={type}
      className={cn(styles.input, invalid && styles.invalid, className)}
      aria-invalid={invalid}
      {...rest}
    />
  );
}
