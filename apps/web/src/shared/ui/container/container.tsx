import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";
import styles from "./container.module.css";

export type ContainerWidth = "sm" | "md" | "lg" | "xl";

export interface ContainerProps extends HTMLAttributes<HTMLDivElement> {
  readonly width?: ContainerWidth;
  readonly children: ReactNode;
}

export function Container({ width = "lg", className, children, ...rest }: ContainerProps) {
  return (
    <div className={cn(styles.container, styles[width], className)} {...rest}>
      {children}
    </div>
  );
}
