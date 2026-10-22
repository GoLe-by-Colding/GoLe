import { cn } from "@shared/lib";
import type { MascotArt } from "./types";

/**
 * 2026-10-21 옆모습 브릭 고래. 작은 로고에서 읽히는 흰 눈·미소와 위로 열린 꼬리.
 * `mark.svg`가 정본이며 아이콘 생성기가 아래 다섯 경로와 OG의 동기화를 검사한다.
 * 기존 `side-brick` 프리셋은 그대로 보관하므로 관리자가 이전 버전으로 돌아갈 수 있다.
 */
const MARK_W = 235.8;
const MARK_H = 132.2;
/** spout 변형에서 스터드 위로 물줄기·브릭이 올라갈 자리(마크 단위). */
const SPOUT_HEADROOM = 40;
const BODY_D =
  "M48 14H146C162.6 14 176 27.4 176 44V76C176 101.4 155.4 122 130 122H48C21.5 122 0 100.5 0 74V62C0 35.5 21.5 14 48 14ZM57 0H75C78.3 0 81 2.7 81 6V22H51V6C51 2.7 53.7 0 57 0ZM135 0H153C156.3 0 159 2.7 159 6V22H129V6C129 2.7 131.7 0 135 0ZM163 63C181 66 185 53 194 40C202 28 215 22 229 22C233 22 235.8 26 234 30L226 49C221 61 212 68 200 72C213 72 226 78 231 89C233 94 230 99 225 99H211C195 99 181 92 174 81H163V63ZM87 105C96 104 108 108 115 118C119 123 116 129 111 131C99 132.2 88 126 82 118C78 113 81 107 87 105Z";
const TOP_D =
  "M6 39C14.4 23.9 30.4 14 48 14H146C159 14 170.2 22.3 174.3 34L176 41H12C8 41 6 40 6 39ZM57 0H75C78.3 0 81 2.7 81 6V22H51V6C51 2.7 53.7 0 57 0ZM135 0H153C156.3 0 159 2.7 159 6V22H129V6C129 2.7 131.7 0 135 0Z";
const GOLD_D = "M94 0H116C119.3 0 122 2.7 122 6V22H88V6C88 2.7 90.7 0 94 0Z";
const FACE_D =
  "M48 53C40.8 53 35 58.8 35 66C35 73.2 40.8 79 48 79C55.2 79 61 73.2 61 66C61 58.8 55.2 53 48 53ZM44 88C45.7 86.5 45.8 84 44.3 82.4C42.8 80.8 40.3 80.6 38.7 82.1C35.1 85.3 29.7 85.3 26.1 82.1C24.5 80.6 22 80.8 20.5 82.4C19 84 19.1 86.5 20.8 88C27.5 94 37.3 94 44 88Z";
const GLINT_D =
  "M49 55C52.9 55 56 58.1 56 62C56 65.9 52.9 69 49 69C45.1 69 42 65.9 42 62C42 58.1 45.1 55 49 55Z";

/**
 * 분수 배치. 아래 물줄기·브릭은 원본 옆모습 마크(9f581c70)의 좌표 그대로다 — 가운데 스터드 윗면 가운데가
 * (111, -5)였다. 새 골드 스터드 윗면 가운데(106, 0)로 옮기고, 마크가 작아진 비율(약 0.88)만큼 줄인다.
 */
const SPOUT_PLACE = "translate(106 0) scale(0.88) translate(-111 5)";

/** 1×2 브릭 한 개 — 몸 34×17 위에 스터드 두 개. */
function SpoutBrick({ x, y, className }: { x: number; y: number; className: string }) {
  return (
    <g className={cn("transform-fill origin-center", className)}>
      <rect x={x} y={y} width="34" height="17" rx="3" />
      <rect x={x + 6} y={y - 5} width="9" height="6" rx="2" />
      <rect x={x + 19} y={y - 5} width="9" height="6" rx="2" />
    </g>
  );
}

function SideBrickArt({ spout }: { readonly spout: boolean }) {
  return (
    <>
      {spout ? (
        // 몸보다 먼저 그려, 떨어지는 브릭이 몸 뒤로 사라지게 한다.
        <g transform={SPOUT_PLACE}>
          <path
            d="M96 3C90-13 84-21 75-28M100 1C102-16 108-28 118-36M104 4C116-9 130-15 144-15"
            strokeWidth="4"
            strokeLinecap="round"
            opacity="0.75"
            className="stroke-brand-300 [stroke-dasharray:42_10] motion-safe:animate-spout-stream"
          />
          <SpoutBrick
            x={60}
            y={-30}
            className="motion-safe:animate-spout-brick-a fill-accent-500"
          />
          <SpoutBrick
            x={114}
            y={-42}
            className="motion-safe:animate-spout-brick-b fill-brand-400"
          />
          <SpoutBrick
            x={154}
            y={-20}
            className="motion-safe:animate-spout-brick-c fill-brand-300"
          />
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
      <path
        d={TOP_D}
        fill="#6082F7"
        className="fill-[var(--gole-mark-top,var(--gole-mark-body,var(--color-brand-400)))]"
      />
      <path d={FACE_D} fill="#FFFFFF" className="fill-[var(--gole-mark-face,var(--color-white))]" />
      <path
        d={GLINT_D}
        fill="#131E4F"
        className="fill-[var(--gole-mark-glint,var(--color-brand-950))]"
      />
    </>
  );
}

export const sideBrickSoft: MascotArt = {
  viewBox: [0, 0, MARK_W, MARK_H],
  spoutViewBox: [0, -SPOUT_HEADROOM, MARK_W, MARK_H + SPOUT_HEADROOM],
  hasSpout: true,
  themable: true,
  Art: SideBrickArt,
};
