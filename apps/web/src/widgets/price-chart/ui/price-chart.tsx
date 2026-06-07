import type { PricePoint } from "@entities/pricing";

export interface PriceChartProps {
  readonly points: readonly PricePoint[];
  readonly width?: number;
  readonly height?: number;
}

const PADDING = 4;

/**
 * 의존성 없는 SVG 라인/영역 차트. 체결 시계열(오름차순)을 그린다.
 */
export function PriceChart({ points, width = 280, height = 72 }: PriceChartProps) {
  if (points.length < 2) {
    return (
      <div
        className="flex items-center justify-center text-xs text-neutral-400"
        style={{ height }}
      >
        데이터 부족
      </div>
    );
  }

  const prices = points.map((p) => p.price);
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  const span = max - min || 1;
  const innerW = width - PADDING * 2;
  const innerH = height - PADDING * 2;

  const coords = points.map((p, i) => {
    const x = PADDING + (i / (points.length - 1)) * innerW;
    const y = PADDING + (1 - (p.price - min) / span) * innerH;
    return { x, y };
  });

  const line = coords.map((c, i) => `${i === 0 ? "M" : "L"}${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(" ");
  const first = coords[0];
  const last = coords[coords.length - 1];
  const area =
    first !== undefined && last !== undefined
      ? `${line} L${last.x.toFixed(1)},${(height - PADDING).toFixed(1)} L${first.x.toFixed(1)},${(height - PADDING).toFixed(1)} Z`
      : "";

  return (
    <svg
      width="100%"
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      role="img"
      aria-label="시세 추이"
    >
      <path d={area} fill="var(--color-brand-50)" />
      <path
        d={line}
        fill="none"
        stroke="var(--color-brand-500)"
        strokeWidth={2}
        strokeLinejoin="round"
        strokeLinecap="round"
      />
    </svg>
  );
}
