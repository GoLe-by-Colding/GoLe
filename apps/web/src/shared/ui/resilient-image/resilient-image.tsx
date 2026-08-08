"use client";

import { useState, type ImgHTMLAttributes } from "react";
import { cn } from "@shared/lib";
import { Logo } from "../logo";

export interface ResilientImageProps extends ImgHTMLAttributes<HTMLImageElement> {
  readonly src: string;
  readonly alt: string;
}

/** 이미지 원본이 없거나 스토리지가 일시 장애여도 깨진 아이콘 대신 GoLe 브랜드 마크를 보여준다. */
export function ResilientImage({ src, alt, className, onError, ...props }: ResilientImageProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const failed = failedSrc === src;

  if (failed) {
    return (
      <span
        role={alt === "" ? undefined : "img"}
        aria-label={alt === "" ? undefined : `${alt} 이미지 준비 중`}
        aria-hidden={alt === "" ? true : undefined}
        className={cn(
          "grid place-items-center overflow-hidden bg-[radial-gradient(circle_at_25%_25%,#fff_0_2px,transparent_3px),linear-gradient(145deg,#eff6ff,#dbeafe)] bg-[size:18px_18px,auto]",
          className,
        )}
        data-image-fallback="true"
      >
        <Logo size={54} showWordmark={false} className="opacity-55" />
      </span>
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      {...props}
      src={src}
      alt={alt}
      className={className}
      onError={(event) => {
        setFailedSrc(src);
        onError?.(event);
      }}
    />
  );
}
