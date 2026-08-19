"use client";

import { useId, useState } from "react";

export interface LineChartPoint {
  readonly value: number;
  readonly label: string;
}

export interface LineChartProps {
  readonly points: readonly LineChartPoint[];
  readonly height?: number;
  /** 값 포맷터(툴팁). 기본 toLocaleString. */
  readonly formatValue?: (value: number) => string;
  /**
   * 가격축·현재가 배지 전용 포맷터. 기본값은 {@link formatValue}.
   *
   * <p>축은 눈금이 넷이라 툴팁과 같은 자릿수를 쓰면 숫자 벽이 되어 정작 읽어야 할 선을 가린다.
   * 축약 표기를 넘기면 축만 짧아지고 툴팁은 정확한 값을 유지한다.
   */
  readonly formatAxisValue?: (value: number) => string;
  readonly emptyText?: string;
}

const W = 720;
const H = 280;
const PAD_T = 16;
const PAD_B = 20;
const PAD_L = 6;
const PAD_R = 12; // 마지막 체결점 마커가 잘리지 않을 만큼만
const GRID_LINES = 3;

/**
 * 모노톤 큐빅의 접선 길이 계수.
 * 1이면 표준 Hermite(h/3)라 점마다 봉우리가 크게 부풀어 체결가처럼 노이즈가 큰
 * 계열은 파형처럼 보인다. 낮출수록 실제 경로에 붙어 시세 차트의 인상이 또렷해진다.
 */
const TENSION = 0.55;

interface Pt {
  readonly x: number;
  readonly y: number;
}

/**
 * 모노톤 큐빅(Fritsch–Carlson) 보간으로 라인 path를 만든다.
 * 데이터 사이에 가짜 봉우리/오버슈트가 생기지 않아 시세 차트에 적합하다.
 */
function smoothPath(pts: readonly Pt[]): string {
  const n = pts.length;
  if (n < 2) return "";
  if (n === 2) {
    return `M${pts[0]!.x.toFixed(1)},${pts[0]!.y.toFixed(1)} L${pts[1]!.x.toFixed(1)},${pts[1]!.y.toFixed(1)}`;
  }
  const dx: number[] = [];
  const slope: number[] = [];
  for (let i = 0; i < n - 1; i += 1) {
    const h = pts[i + 1]!.x - pts[i]!.x;
    dx.push(h);
    slope.push((pts[i + 1]!.y - pts[i]!.y) / h);
  }
  const m = new Array<number>(n);
  m[0] = slope[0]!;
  m[n - 1] = slope[n - 2]!;
  for (let i = 1; i < n - 1; i += 1) {
    m[i] = slope[i - 1]! * slope[i]! <= 0 ? 0 : (slope[i - 1]! + slope[i]!) / 2;
  }
  for (let i = 0; i < n - 1; i += 1) {
    if (slope[i] === 0) {
      m[i] = 0;
      m[i + 1] = 0;
    } else {
      const a = m[i]! / slope[i]!;
      const b = m[i + 1]! / slope[i]!;
      const s = a * a + b * b;
      if (s > 9) {
        const t = 3 / Math.sqrt(s);
        m[i] = t * a * slope[i]!;
        m[i + 1] = t * b * slope[i]!;
      }
    }
  }
  let d = `M${pts[0]!.x.toFixed(1)},${pts[0]!.y.toFixed(1)}`;
  for (let i = 0; i < n - 1; i += 1) {
    const arm = (dx[i]! / 3) * TENSION;
    const cp1x = pts[i]!.x + arm;
    const cp1y = pts[i]!.y + m[i]! * arm;
    const cp2x = pts[i + 1]!.x - arm;
    const cp2y = pts[i + 1]!.y - m[i + 1]! * arm;
    d += ` C${cp1x.toFixed(1)},${cp1y.toFixed(1)} ${cp2x.toFixed(1)},${cp2y.toFixed(1)} ${pts[i + 1]!.x.toFixed(1)},${pts[i + 1]!.y.toFixed(1)}`;
  }
  return d;
}

/**
 * 의존성 없는 인터랙티브 SVG 라인 차트(KREAM 스타일).
 * 균일 스케일(viewBox)로 왜곡 없이 렌더하고, 가로 그리드 + 우측 가격축 + 호버 툴팁을 제공한다.
 *
 * 레이아웃은 "플롯(가변 폭 SVG) + 가격축(고정 폭 컬럼)"의 flex 행이다.
 * 축 눈금을 SVG `<text>`로 그리면 viewBox 배율만큼 글자가 함께 확대·축소돼
 * 넓은 화면에서는 비대해지고 좁은 화면에서는 읽을 수 없게 된다. 그래서 축은
 * HTML로 그리고, 축이 차지할 폭도 viewBox 비율이 아니라 px로 고정한다.
 *
 * 도메인 의존이 없는 일반 UI라 shared/ui 에 둔다.
 */
export function LineChart({
  points,
  height = 260,
  formatValue = (v) => v.toLocaleString(),
  formatAxisValue,
  emptyText = "데이터가 부족해요",
}: LineChartProps) {
  const formatAxis = formatAxisValue ?? formatValue;
  const [hover, setHover] = useState<number | null>(null);
  const gradientId = useId().replaceAll(":", "");

  if (points.length < 2) {
    return (
      <div
        className="flex items-center justify-center rounded-lg bg-neutral-50 text-sm text-neutral-400"
        style={{ height }}
      >
        {emptyText}
      </div>
    );
  }

  const n = points.length;
  const values = points.map((p) => p.value);
  const rawMin = Math.min(...values);
  const rawMax = Math.max(...values);
  const rawSpan = rawMax - rawMin;
  const domainPadding =
    rawSpan === 0 ? Math.max(Math.abs(rawMax) * 0.05, 1) : Math.max(rawSpan * 0.08, 1);
  const min = rawMin - domainPadding;
  const max = rawMax + domainPadding;
  const span = max - min;
  const innerW = W - PAD_L - PAD_R;
  const innerH = H - PAD_T - PAD_B;

  const xAt = (i: number) => PAD_L + (i / (n - 1)) * innerW;
  const yAt = (v: number) => PAD_T + (1 - (v - min) / span) * innerH;

  const coords = points.map((p, i) => ({ x: xAt(i), y: yAt(p.value) }));
  const line = smoothPath(coords);
  const area = `${line} L${xAt(n - 1).toFixed(1)},${H - PAD_B} L${PAD_L},${H - PAD_B} Z`;

  // 가로 그리드 + 우측 가격 눈금
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
  // 상단에 붙은 점은 위쪽 공간이 없어 툴팁을 아래로 뒤집는다.
  const tooltipBelow = hc ? hc.y / H < 0.3 : false;
  /**
   * 현재가 배지에 가장 가까운 눈금은 항상 숨긴다.
   * 겹침 여부는 렌더된 px 간격으로 결정되는데 그 값은 컨테이너 폭에 따라 달라지므로,
   * viewBox 단위의 고정 임계값으로는 어떤 크기에서도 안전하다고 보장할 수 없다.
   */
  const badgeNearestTick = grid.reduce(
    (best, g, i) => (Math.abs(g.y - last.y) < Math.abs(grid[best]!.y - last.y) ? i : best),
    0,
  );

  function handleMove(event: React.PointerEvent<SVGSVGElement>) {
    const rect = event.currentTarget.getBoundingClientRect();
    const viewX = ((event.clientX - rect.left) / rect.width) * W;
    const ratio = (viewX - PAD_L) / innerW;
    const i = Math.max(0, Math.min(n - 1, Math.round(ratio * (n - 1))));
    setHover(i);
  }

  function handleKeyDown(event: React.KeyboardEvent<SVGSVGElement>) {
    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
    event.preventDefault();
    if (event.key === "Home") {
      setHover(0);
      return;
    }
    if (event.key === "End") {
      setHover(n - 1);
      return;
    }
    const current = hover ?? n - 1;
    setHover(event.key === "ArrowLeft" ? Math.max(0, current - 1) : Math.min(n - 1, current + 1));
  }

  const xLabelIndexes = Array.from(new Set([0, Math.floor((n - 1) / 2), n - 1]));

  return (
    <div className="@container w-full select-none">
      {/* 플롯 + 가격축. 축 컬럼은 stretch로 플롯 높이를 그대로 받는다. */}
      <div className="flex">
        <div className="relative min-w-0 flex-1">
          <svg
            viewBox={`0 0 ${W} ${H}`}
            role="img"
            aria-label="시세 추이 차트. 좌우 방향키로 날짜별 가격을 확인할 수 있습니다."
            tabIndex={0}
            onPointerMove={handleMove}
            onPointerLeave={() => setHover(null)}
            onFocus={() => setHover(n - 1)}
            onBlur={() => setHover(null)}
            onKeyDown={handleKeyDown}
            className="block w-full touch-pan-y rounded-md outline-none focus-visible:ring-2 focus-visible:ring-brand-100"
            style={{ aspectRatio: `${W} / ${H}` }}
          >
            <title>날짜별 체결가 추이</title>
            <defs>
              <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={lineColor} stopOpacity="0.16" />
                <stop offset="60%" stopColor={lineColor} stopOpacity="0.04" />
                <stop offset="100%" stopColor={lineColor} stopOpacity="0" />
              </linearGradient>
            </defs>

            {/* 가로 그리드 — 맨 아래 줄만 축 기준선이라 실선으로 둔다 */}
            {grid.map((g, i) => {
              const isBaseline = i === GRID_LINES;
              return (
                <line
                  key={i}
                  x1={0}
                  y1={g.y.toFixed(1)}
                  x2={W}
                  y2={g.y.toFixed(1)}
                  stroke="var(--color-neutral-200)"
                  strokeWidth={1}
                  strokeDasharray={isBaseline ? undefined : "2 6"}
                  strokeOpacity={isBaseline ? 1 : 0.85}
                  vectorEffect="non-scaling-stroke"
                />
              );
            })}

            <path d={area} fill={`url(#${gradientId})`} />
            <path
              d={line}
              fill="none"
              stroke={lineColor}
              strokeWidth={2}
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
                strokeDasharray="3 4"
                vectorEffect="non-scaling-stroke"
              />
            ) : null}

            {/* 마지막 체결점: 옅은 후광 + 흰 테두리 점 */}
            <circle cx={last.x} cy={last.y} r={9} fill={lineColor} opacity={0.14} />
            <circle
              cx={last.x}
              cy={last.y}
              r={3.5}
              fill={lineColor}
              stroke="#fff"
              strokeWidth={2}
            />

            {hc ? (
              <circle
                cx={hc.x}
                cy={hc.y}
                r={4.5}
                fill={lineColor}
                stroke="#fff"
                strokeWidth={2.5}
              />
            ) : null}
          </svg>

          {/* 호버 툴팁 — 좌표계가 플롯과 같도록 플롯 컬럼 안에 둔다 */}
          {hp ? (
            <div
              className="pointer-events-none absolute z-10 rounded-md bg-neutral-900 px-2.5 py-1.5 text-center shadow-lift"
              style={{
                left: `clamp(52px, ${(hc!.x / W) * 100}%, calc(100% - 52px))`,
                top: `${(hc!.y / H) * 100}%`,
                transform: tooltipBelow
                  ? "translate(-50%, 14px)"
                  : "translate(-50%, calc(-100% - 14px))",
              }}
            >
              <div className="whitespace-nowrap text-sm font-bold text-white">
                {formatValue(hp.value)}
              </div>
              <div className="whitespace-nowrap text-[11px] text-neutral-400">{hp.label}</div>
              {/* 데이터 점을 가리키는 꼬리 */}
              <span
                aria-hidden="true"
                className="absolute left-1/2 h-2 w-2 -translate-x-1/2 rotate-45 bg-neutral-900"
                style={tooltipBelow ? { top: -4 } : { bottom: -4 }}
              />
            </div>
          ) : null}
        </div>

        {/* 우측 가격축 */}
        <div className="relative w-[72px] shrink-0 pl-2 @lg:w-[92px] @lg:pl-2.5">
          {grid.map((g, i) => {
            if (i === badgeNearestTick) return null;
            // 좁은 폭에서는 눈금 간격이 글자 높이에 가까워지므로 양 끝만 남긴다.
            const interior = i !== 0 && i !== GRID_LINES;
            return (
              <span
                key={i}
                className={`absolute -translate-y-1/2 text-[10px] tabular-nums whitespace-nowrap text-neutral-400 @lg:text-[11px] ${
                  interior ? "hidden @lg:block" : ""
                }`}
                style={{ top: `${(g.y / H) * 100}%` }}
              >
                {formatAxis(Math.round(g.v))}
              </span>
            );
          })}
          <span
            className="absolute -translate-y-1/2 rounded-sm px-1.5 py-0.5 text-[10px] font-bold tabular-nums whitespace-nowrap text-white @lg:text-[11px]"
            style={{ top: `${(last.y / H) * 100}%`, backgroundColor: lineColor }}
          >
            {formatAxis(values[n - 1]!)}
          </span>
        </div>
      </div>

      {/* X축 기간 라벨 — 플롯 폭에만 맞추고 각 라벨을 실제 데이터 x 좌표에 정렬한다 */}
      <div className="relative mt-1.5 mr-[72px] h-4 @lg:mr-[92px]">
        {xLabelIndexes.map((index, i) => (
          <span
            key={index}
            className="absolute top-0 text-[11px] tabular-nums whitespace-nowrap text-neutral-400"
            style={{
              left: `${(xAt(index) / W) * 100}%`,
              transform:
                i === 0
                  ? "none"
                  : i === xLabelIndexes.length - 1
                    ? "translateX(-100%)"
                    : "translateX(-50%)",
            }}
          >
            {points[index]!.label}
          </span>
        ))}
      </div>

      <span className="sr-only" aria-live="polite">
        {hp ? `${hp.label}, ${formatValue(hp.value)}` : ""}
      </span>
    </div>
  );
}
