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

const W = 720;
const H = 280;
const PAD_T = 18;
const PAD_B = 26;
const PAD_L = 6;
const PAD_R = 64; // 우측 가격 라벨 공간
const GRID_LINES = 4;

/**
 * 의존성 없는 인터랙티브 SVG 라인 차트(KREAM 스타일).
 * 균일 스케일(viewBox)로 왜곡 없이 렌더하고, 가로 그리드 + 우측 가격축 + 호버 툴팁을 제공한다.
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
  const innerW = W - PAD_L - PAD_R;
  const innerH = H - PAD_T - PAD_B;

  const xAt = (i: number) => PAD_L + (i / (n - 1)) * innerW;
  const yAt = (v: number) => PAD_T + (1 - (v - min) / span) * innerH;

  const coords = points.map((p, i) => ({ x: xAt(i), y: yAt(p.value) }));
  const line = coords
    .map((c, i) => `${i === 0 ? "M" : "L"}${c.x.toFixed(1)},${c.y.toFixed(1)}`)
    .join(" ");
  const area = `${line} L${xAt(n - 1).toFixed(1)},${H - PAD_B} L${PAD_L},${H - PAD_B} Z`;

  // 가로 그리드 + 우측 가격 라벨
  const grid = Array.from({ length: GRID_LINES + 1 }, (_, i) => {
    const t = i / GRID_LINES;
    const y = PAD_T + t * innerH;
    const v = max - t * span;
    return { y, v };
  });

  const last = coords[n - 1]!;
  const up = values[n - 1]! >= values[0]!;
  const lineColor = up ? "var(--color-brand-600)" : "var(--color-danger)";
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
        role="img"
        aria-label="시세 추이 차트"
        onPointerMove={handleMove}
        onPointerLeave={() => setHover(null)}
        className="block"
      >
        <defs>
          <linearGradient id="line-chart-area" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={lineColor} stopOpacity="0.18" />
            <stop offset="100%" stopColor={lineColor} stopOpacity="0" />
          </linearGradient>
        </defs>

        {/* 가로 그리드 + 우측 가격 라벨 */}
        {grid.map((g, i) => (
          <g key={i}>
            <line
              x1={PAD_L}
              y1={g.y.toFixed(1)}
              x2={W - PAD_R}
              y2={g.y.toFixed(1)}
              stroke="var(--color-neutral-100)"
              strokeWidth={1}
              vectorEffect="non-scaling-stroke"
            />
            <text
              x={W - PAD_R + 8}
              y={g.y + 4}
              fontSize="13"
              fill="var(--color-neutral-400)"
            >
              {formatValue(Math.round(g.v))}
            </text>
          </g>
        ))}

        <path d={area} fill="url(#line-chart-area)" />
        <path
          d={line}
          fill="none"
          stroke={lineColor}
          strokeWidth={2.5}
          strokeLinejoin="round"
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />

        {hc ? (
          <line
            x1={hc.x}
            y1={PAD_T}
            x2={hc.x}
            y2={H - PAD_B}
            stroke="var(--color-neutral-300)"
            strokeWidth={1}
            strokeDasharray="4 4"
            vectorEffect="non-scaling-stroke"
          />
        ) : null}
        <circle cx={last.x} cy={last.y} r={4} fill={lineColor} />
        {hc ? (
          <circle cx={hc.x} cy={hc.y} r={5} fill={lineColor} stroke="#fff" strokeWidth={2} />
        ) : null}
      </svg>

      {/* X축 기간 라벨 */}
      <div className="mt-1 flex justify-between pr-14 text-[11px] text-neutral-400">
        <span>{points[0]!.label}</span>
        <span>{points[n - 1]!.label}</span>
      </div>

      {/* 호버 툴팁 */}
      {hp ? (
        <div
          className="pointer-events-none absolute top-0 z-10 -translate-x-1/2 rounded-lg bg-neutral-900 px-2.5 py-1.5 text-center shadow-lift"
          style={{ left: `${(hover! / (n - 1)) * ((innerW / W) * 100) + (PAD_L / W) * 100}%` }}
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
