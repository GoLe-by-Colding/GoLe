import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@shared/lib";

export type ContainerWidth = "sm" | "md" | "lg" | "xl";

const WIDTH: Record<ContainerWidth, string> = {
  sm: "max-w-[640px]",
  md: "max-w-[820px]",
  lg: "max-w-[1080px]",
  xl: "max-w-[1280px]",
};

export interface ContainerProps extends HTMLAttributes<HTMLDivElement> {
  readonly width?: ContainerWidth;
  readonly children: ReactNode;
}

export function Container({ width = "lg", className, children, ...rest }: ContainerProps) {
  return (
    <div className={cn("w-full mx-auto px-5", WIDTH[width], className)} {...rest}>
      {children}
    </div>
  );
}
