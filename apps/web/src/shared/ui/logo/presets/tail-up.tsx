import { cn } from "@shared/lib";
import type { MascotArt } from "./types";

/*
 * 꼬리 든 고래 — `6734f0f8`(2026-10-09)의 고래 마크. 다시 그리지 않고 그때의 SVG 를 그대로 옮겼다.
 * 원본: `git show 6734f0f8:apps/web/src/shared/ui/logo/logo.tsx`
 */
const MARK_W = 269;
const MARK_H = 198.3;
/** 고래 실루엣 + 파란 스터드 2개. 눈은 반대 방향으로 감은 원이라 nonzero 규칙에서 구멍이 된다. */
const BODY_D =
  "M80.2 57.2C86.7 73.5 89 88.3 116.5 88.3L214 88.3C244.4 88.3 269 112.9 269 143.3C269 173.6 244.4 198.3 214 198.3L159 198.3C109 198.3 71.5 180.8 62.8 145.8C59 133.3 59.4 92.6 50 69.4C44.9 56.7 16.2 46.7 5.8 46.5C3.2 46.4 1 44.7 0.4 42.3C-0.3 39.9 0.7 37.3 2.8 35.9C17.4 26.5 35.8 23.8 55.7 40.1C58.7 14.6 73.8 3.7 90.9 0.3C93.3 -0.2 95.9 1 97.1 3.2C98.2 5.5 97.9 8.2 96.1 10C88.7 17.4 75 44.6 80.2 57.2ZM119 90.8V78.5A6.5 6.5 0 0 1 125.5 72H137.5A6.5 6.5 0 0 1 144 78.5V90.8ZM189 90.8V78.5A6.5 6.5 0 0 1 195.5 72H207.5A6.5 6.5 0 0 1 214 78.5V90.8ZM216.5 135.8A12.5 12.5 0 1 0 241.5 135.8A12.5 12.5 0 1 0 216.5 135.8Z";
/** 가운데 골드 스터드. 몸보다 먼저 그려 아랫단을 몸 속에 묻는다. */
const GOLD_D = "M154 90.8V78.5A6.5 6.5 0 0 1 160.5 72H172.5A6.5 6.5 0 0 1 179 78.5V90.8Z";

/** 1×2 브릭 한 개 — 몸 34×17 위에 스터드 두 개. 분수 그룹 안의 좌표다. */
function SpoutBrick({ x, y, className }: { x: number; y: number; className: string }) {
  return (
    <g className={cn("gole-spout-brick", className)}>
      <rect x={x} y={y} width="34" height="17" rx="3" />
      <rect x={x + 6} y={y - 5} width="9" height="6" rx="2" />
      <rect x={x + 19} y={y - 5} width="9" height="6" rx="2" />
    </g>
  );
}

function TailUpArt({ spout }: { readonly spout: boolean }) {
  return (
    <>
      {spout ? (
        // 몸보다 먼저 그려, 떨어지는 브릭이 등 뒤로 사라지게 한다.
        <g transform="matrix(-1 0 0 1 448 0)">
          <path
            d="M220 81C214 65 208 57 199 50M224 79C226 62 232 50 242 42M228 82C240 69 254 63 268 63"
            strokeWidth="4"
            strokeLinecap="round"
            opacity="0.75"
            className="gole-spout-stream stroke-brand-300"
          />
          <SpoutBrick x={184} y={21} className="gole-spout-brick-a fill-accent-500" />
          <SpoutBrick x={238} y={9} className="gole-spout-brick-b fill-brand-400" />
          <SpoutBrick x={278} y={31} className="gole-spout-brick-c fill-brand-300" />
        </g>
      ) : null}
      {/* fill 속성은 CSS가 없을 때의 대비값이다. 클래스(토큰 + 변수)가 항상 이긴다. */}
      <path
        d={GOLD_D}
        fill="#EAB308"
        className="fill-[var(--gole-mark-accent,var(--color-accent-500))]"
      />
      <path
        d={BODY_D}
        fill="#1D4ED8"
        className="fill-[var(--gole-mark-body,var(--color-brand-600))]"
      />
    </>
  );
}

export const tailUp: MascotArt = {
  viewBox: [0, 0, MARK_W, MARK_H],
  hasSpout: true,
  themable: true,
  Art: TailUpArt,
};
