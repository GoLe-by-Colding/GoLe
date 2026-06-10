"use client";

import { useState } from "react";

export interface LineChartPoint {
  readonly value: number;
  readonly label: string;
}

export interface LineChartProps {
  readonly points: readonly LineChartPoint[];
  readonly height?: number;
  /** 값 포맷터(툴팁/축). 기본 toLocaleString. */
  readonly formatValue?: (value: number) => string;
  readonly emptyText?: string;
}

const W = 640;
const H = 260;
const PAD_X = 10;
const PAD_Y = 16;

/**
 * 의존성 없는 인터랙티브 SVG 라인 차트(그라데이션 영역 + 호버 크로스헤어/툴팁).
 * 도메인 의존이 없는 일반 UI라 shared/ui 에 둔다.
 */
export function LineChart({
  points,
  height = 260,
  formatValue = (v) => v.toLocaleString(),
  emptyText = "데이터가 부족해요",
}: LineChartProps) {
  const [hover, setHover] = useState<number | null>(null);

  if (points.length < 2) {
    return (
      <div
        className="flex items-center justify-center rounded-xl bg-neutral-50 text-sm text-neutral-400"
        style={{ height }}
      >
        {emptyText}
      </div>
    );
  }

  const n = points.length;
  const values = points.map((p) => p.value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const innerW = W - PAD_X * 2;
  const innerH = H - PAD_Y * 2;

  const xAt = (i: number) => PAD_X + (i / (n - 1)) * innerW;
  const yAt = (v: number) => PAD_Y + (1 - (v - min) / span) * innerH;

  const coords = points.map((p, i) => ({ x: xAt(i), y: yAt(p.value) }));
  const line = coords
    .map((c, i) => `${i === 0 ? "M" : "L"}${c.x.toFixed(1)},${c.y.toFixed(1)}`)
    .join(" ");
  const area = `${line} L${xAt(n - 1).toFixed(1)},${H - PAD_Y} L${PAD_X},${H - PAD_Y} Z`;

  const last = coords[n - 1]!;
  const hc = hover !== null ? coords[hover] : null;
  const hp = hover !== null ? points[hover] : null;

  function handleMove(event: React.PointerEvent<SVGSVGElement>) {
    const rect = event.currentTarget.getBoundingClientRect();
    const ratio = (event.clientX - rect.left) / rect.width;
    const i = Math.max(0, Math.min(n - 1, Math.round(ratio * (n - 1))));
    setHover(i);
  }

  return (
    <div className="relative w-full select-none">
      <svg
        viewBox={`0 0 ${W} ${H}`}
        width="100%"
        style={{ height }}
        preserveAspectRatio="none"
        role="img"
        aria-label="시세 추이 차트"
        onPointerMove={handleMove}
        onPointerLeave={() => setHover(null)}
      >
        <defs>
          <linearGradient id="line-chart-area" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--color-brand-500)" stopOpacity="0.22" />
            <stop offset="100%" stopColor="var(--color-brand-500)" stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d={area} fill="url(#line-chart-area)" />
        <path
          d={line}
          fill="none"
          stroke="var(--color-brand-600)"
          strokeWidth={2.5}
          strokeLinejoin="round"
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />
        {hc ? (
          <line
            x1={hc.x}
            y1={PAD_Y}
            x2={hc.x}
            y2={H - PAD_Y}
            stroke="var(--color-neutral-300)"
            strokeWidth={1}
            strokeDasharray="3 3"
            vectorEffect="non-scaling-stroke"
          />
        ) : null}
        <circle cx={last.x} cy={last.y} r={4} fill="var(--color-brand-600)" />
        {hc ? (
          <circle cx={hc.x} cy={hc.y} r={5} fill="var(--color-brand-600)" stroke="#fff" strokeWidth={2} />
        ) : null}
      </svg>

      <span className="pointer-events-none absolute right-1 top-0 text-[11px] font-medium text-neutral-400">
        {formatValue(max)}
      </span>
      <span className="pointer-events-none absolute bottom-5 right-1 text-[11px] font-medium text-neutral-400">
        {formatValue(min)}
      </span>

      <div className="mt-1 flex justify-between text-[11px] text-neutral-400">
        <span>{points[0]!.label}</span>
        <span>{points[n - 1]!.label}</span>
      </div>

      {hp ? (
        <div
          className="pointer-events-none absolute top-0 z-10 -translate-x-1/2 rounded-lg bg-neutral-900 px-2.5 py-1.5 text-center shadow-lift"
          style={{ left: `${(hover! / (n - 1)) * 100}%` }}
        >
          <div className="whitespace-nowrap text-sm font-bold text-white">
            {formatValue(hp.value)}
          </div>
          <div className="whitespace-nowrap text-[11px] text-neutral-400">{hp.label}</div>
        </div>
      ) : null}
    </div>
  );
}
