import type { ComponentPropsWithoutRef } from "react";

export type BellIconProps = Omit<ComponentPropsWithoutRef<"svg">, "children">;

/** 알림을 나타내는 장식용 선형 아이콘. */
export function BellIcon({ className, ...props }: BellIconProps) {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      {...props}
    >
      <path d="M18 8a6 6 0 0 0-12 0c0 4.5-1.4 6-2.75 7.33A1 1 0 0 0 4 17h16a1 1 0 0 0 .75-1.67C19.4 14 18 12.5 18 8Z" />
      <path d="M10 21h4" />
    </svg>
  );
}
