"use client";

import { useEffect, useRef, useState, type ImgHTMLAttributes, type ReactNode } from "react";
import { cn } from "@shared/lib";

export interface MediaImageProps extends Omit<
  ImgHTMLAttributes<HTMLImageElement>,
  "src" | "onError"
> {
  readonly src: string | null | undefined;
  readonly fallback?: ReactNode;
  readonly fallbackClassName?: string;
}

/**
 * 외부 미디어가 없거나 로드에 실패해도 브라우저의 깨진 이미지 UI를 노출하지 않는다.
 * src가 바뀌면 새 주소를 다시 시도한다.
 */
export function MediaImage({
  src,
  alt,
  fallback = "이미지 없음",
  fallbackClassName,
  className,
  ...rest
}: MediaImageProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const imageRef = useRef<HTMLImageElement>(null);
  const unavailable = !src || failedSrc === src;

  useEffect(() => {
    const image = imageRef.current;
    if (src && image?.complete && image.naturalWidth === 0) {
      // SSR 이미지가 hydration 전에 실패하면 error 이벤트가 재생되지 않을 수 있다.
      setFailedSrc(src);
    }
  }, [src]);

  if (unavailable) {
    return (
      <span
        role={alt ? "img" : undefined}
        aria-label={alt || undefined}
        aria-hidden={alt ? undefined : "true"}
        className={cn(
          "flex items-center justify-center bg-neutral-50 text-xs font-semibold text-neutral-400",
          className,
          fallbackClassName,
        )}
        data-image-fallback="true"
      >
        {fallback}
      </span>
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      ref={imageRef}
      src={src}
      alt={alt}
      className={className}
      onError={() => setFailedSrc(src)}
      {...rest}
    />
  );
}
