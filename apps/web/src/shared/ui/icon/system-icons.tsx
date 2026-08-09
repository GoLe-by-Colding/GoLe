import type { ComponentPropsWithoutRef, ReactNode } from "react";

type IconProps = Omit<ComponentPropsWithoutRef<"svg">, "children">;

interface IconFrameProps extends IconProps {
  readonly children: ReactNode;
}

function IconFrame({ children, className, ...props }: IconFrameProps) {
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
      {children}
    </svg>
  );
}

export function BrickIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M4 9.5h16v9H4z" />
      <path d="M7 9.5V6h4v3.5M13 9.5V6h4v3.5M4 14h16" />
    </IconFrame>
  );
}

export function MessageCircleIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M20 11.5a7.5 7.5 0 0 1-8 7.5 8.8 8.8 0 0 1-3.4-.7L4 20l1.4-4A7.2 7.2 0 0 1 4 11.5 7.5 7.5 0 0 1 12 4a7.5 7.5 0 0 1 8 7.5Z" />
      <path d="M8.5 11.5h.01M12 11.5h.01M15.5 11.5h.01" strokeWidth="2.4" />
    </IconFrame>
  );
}

export function ShoppingBagIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M5 8h14l1 12H4L5 8Z" />
      <path d="M9 9V6a3 3 0 0 1 6 0v3" />
    </IconFrame>
  );
}

export function PackageIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="m4 7 8-4 8 4-8 4-8-4Z" />
      <path d="M4 7v10l8 4 8-4V7M12 11v10M8 5l8 4" />
    </IconFrame>
  );
}

export function TrendingUpIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="m4 17 5-5 4 4 7-8" />
      <path d="M15 8h5v5" />
    </IconFrame>
  );
}

export function FlagIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M5 21V4" />
      <path d="M5 5h10l-1.5 3L15 11H5" />
    </IconFrame>
  );
}

export interface HeartIconProps extends IconProps {
  readonly filled?: boolean;
}

export function HeartIcon({ filled = false, ...props }: HeartIconProps) {
  return (
    <IconFrame fill={filled ? "currentColor" : "none"} {...props}>
      <path d="M20.8 4.6a5.4 5.4 0 0 0-7.6 0L12 5.8l-1.2-1.2a5.4 5.4 0 0 0-7.6 7.6L12 21l8.8-8.8a5.4 5.4 0 0 0 0-7.6Z" />
    </IconFrame>
  );
}

export interface StarIconProps extends IconProps {
  readonly filled?: boolean;
}

export function StarIcon({ filled = false, ...props }: StarIconProps) {
  return (
    <IconFrame fill={filled ? "currentColor" : "none"} {...props}>
      <path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-2.9-5.6 2.9 1.1-6.2L3 9.6l6.2-.9L12 3Z" />
    </IconFrame>
  );
}

export function CheckCircleIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="m8 12 2.5 2.5L16.5 9" />
    </IconFrame>
  );
}

export function UndoIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="m9 8-4 4 4 4" />
      <path d="M5 12h8a6 6 0 0 1 6 6" />
    </IconFrame>
  );
}

export function AlertCircleIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v6M12 17h.01" />
    </IconFrame>
  );
}

export function LoaderIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M21 12a9 9 0 1 1-5.3-8.2" />
    </IconFrame>
  );
}
