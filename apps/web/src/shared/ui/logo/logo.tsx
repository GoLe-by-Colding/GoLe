"use client";

import { useEffect, useRef, useState } from "react";
import { DEFAULT_MASCOT_KEY, type MascotArtwork, type UploadedMascot } from "@gole/core/mascot";
import { env } from "@shared/config";
import { cn } from "@shared/lib";
import { useMascot } from "./mascot-context";
import { MASCOT_ART } from "./presets";

export interface LogoProps {
  /** 마크의 가로 픽셀 크기. 세로는 그 마스코트의 비율로 정해진다. */
  readonly size?: number;
  readonly showWordmark?: boolean;
  readonly className?: string;
  readonly accentClassName?: string;
  /** 히어로처럼 큰 장면에서만 브릭 분수를 표시한다. 분수 자리가 정의된 기본 제공 마스코트만 그린다. */
  readonly spout?: boolean;
  /** `inverse`는 어두운 배경(푸터) 위다. 몸을 흰색으로 바꾸거나 어두운 배경용 이미지를 쓴다. */
  readonly tone?: "default" | "inverse";
  /** 이 마스코트를 그린다(관리자 미리보기). 없으면 사이트에 적용된 마스코트다. */
  readonly mascot?: MascotArtwork;
  /** 높이 상한(px). 정사각형에 가까운 그림이 `size` 폭을 다 쓰면 너무 커지는 목록 카드에서 쓴다. */
  readonly maxHeight?: number;
}

/**
 * GoLe 로고 — 마스코트(고래 마크) + `GoLe` 워드마크. (mascot-assets R4)
 *
 * <p>마스코트는 관리자가 `/admin/mascot`에서 고른다. 기본 제공 그림은 `presets/`에 있고, 업로드 그림은 미디어
 * 경로의 이미지다. 어떤 실패에도 기본 고래(옆모습 브릭 고래)를 그려 화면이 비지 않는다.
 */
export function Logo({
  size = 32,
  showWordmark = true,
  className,
  accentClassName = "text-brand-600",
  spout = false,
  tone = "default",
  mascot,
  maxHeight,
}: LogoProps) {
  const active = useMascot();
  return (
    <span
      className={cn("inline-flex items-center gap-1.5 font-extrabold tracking-tight", className)}
    >
      <MascotMark
        artwork={mascot ?? active}
        size={size}
        maxHeight={maxHeight}
        spout={spout}
        tone={tone}
      />
      {showWordmark ? (
        <span>
          Go<span className={accentClassName}>Le</span>
        </span>
      ) : null}
    </span>
  );
}

interface MarkProps {
  readonly artwork: MascotArtwork;
  readonly size: number;
  readonly maxHeight: number | undefined;
  readonly spout: boolean;
  readonly tone: "default" | "inverse";
}

function MascotMark({ artwork, size, maxHeight, spout, tone }: MarkProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  if (artwork.kind === "UPLOAD") {
    const width = fitWidth(size, maxHeight, artwork.width / artwork.height);
    const src = uploadSrc(artwork, width, tone);
    if (failedSrc !== src) {
      return (
        <UploadedMark
          src={src}
          width={width}
          height={Math.round((width * artwork.height) / artwork.width)}
          onFail={setFailedSrc}
        />
      );
    }
  }
  const art = MASCOT_ART[artwork.kind === "PRESET" ? artwork.id : DEFAULT_MASCOT_KEY];
  const drawSpout = spout && art.hasSpout;
  const box = drawSpout && art.spoutViewBox !== undefined ? art.spoutViewBox : art.viewBox;
  const width = fitWidth(size, maxHeight, box[2] / box[3]);
  const inverse = tone === "inverse";
  return (
    <svg
      width={width}
      height={Math.round((width * box[3]) / box[2])}
      viewBox={box.join(" ")}
      fill="none"
      aria-hidden="true"
      className={cn(
        "shrink-0 overflow-visible",
        // 몸 색을 변수로 받는 그림은 몸만 희게, 색이 박힌 옛 그림은 흰 실루엣으로 그린다.
        inverse && art.themable && "[--gole-mark-body:var(--color-white)]",
        inverse && !art.themable && "brightness-0 invert",
      )}
    >
      <art.Art spout={drawSpout} />
    </svg>
  );
}

function UploadedMark({
  src,
  width,
  height,
  onFail,
}: {
  readonly src: string;
  readonly width: number;
  readonly height: number;
  readonly onFail: (src: string) => void;
}) {
  const imageRef = useRef<HTMLImageElement>(null);
  useEffect(() => {
    const image = imageRef.current;
    // 서버가 그린 이미지가 하이드레이션 전에 실패하면 error 이벤트가 다시 오지 않는다.
    if (image?.complete === true && image.naturalWidth === 0) onFail(src);
  }, [src, onFail]);
  return (
    // 미디어 경로 이미지라 next/image 최적화 대상이 아니다(MediaImage 와 같다).
    // eslint-disable-next-line @next/next/no-img-element
    <img
      ref={imageRef}
      src={src}
      width={width}
      height={height}
      alt=""
      aria-hidden="true"
      decoding="async"
      className="shrink-0 object-contain"
      onError={() => onFail(src)}
    />
  );
}

function fitWidth(size: number, maxHeight: number | undefined, aspect: number): number {
  return maxHeight === undefined ? size : Math.min(size, Math.round(maxHeight * aspect));
}

/** 표시 폭의 2배를 덮는 가장 작은 썸네일 폭. 미디어 서버가 허용하는 폭만 쓴다. */
const THUMB_WIDTHS = [240, 480, 960, 1600] as const;

function uploadSrc(artwork: UploadedMascot, size: number, tone: "default" | "inverse"): string {
  const path =
    tone === "inverse" && artwork.darkImageUrl !== null ? artwork.darkImageUrl : artwork.imageUrl;
  const base = path.startsWith("/api/") ? `${env.publicApiBaseUrl}${path}` : path;
  const width = THUMB_WIDTHS.find((candidate) => candidate >= size * 2) ?? 1600;
  return `${base}?w=${width}`;
}
