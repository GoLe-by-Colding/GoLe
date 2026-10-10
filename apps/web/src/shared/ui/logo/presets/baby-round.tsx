import { cn } from "@shared/lib";
import type { MascotArt } from "./types";

/*
 * 둥근 아기 고래 — `daa6cb9a`(2026-10-09)의 고래 마크. 다시 그리지 않고 그때의 SVG 를 그대로 옮겼다.
 * 원본: `git show daa6cb9a:apps/web/src/shared/ui/logo/logo.tsx`
 */
const MARK_W = 223.3;
const MARK_H = 172.3;
/** spout 변형에서 스터드 위로 물줄기·브릭이 올라갈 자리(마크 단위). */
const SPOUT_HEADROOM = 58;
/** 몸·꼬리·지느러미. 눈과 입은 반대 방향으로 감은 윤곽이라 nonzero 규칙에서 구멍이 된다. */
const BODY_D =
  "M111.6 20C165.6 20 209.6 56 209.6 108C209.6 134 195.6 170 163.6 170C133.6 173 89.6 173 59.6 170C27.6 170 13.6 134 13.6 108C13.6 56 57.6 20 111.6 20ZM64.3 39.1C67.3 47.4 65.6 55.6 60.4 57.5C55.2 59.4 48.5 54.2 45.5 45.9C42.5 37.6 44.2 29.3 49.4 27.4C54.6 25.6 61.3 30.8 64.3 39.1ZM54.6 28.2C55.5 34.3 48.6 40.2 39.3 41.5C30 42.8 21.8 39 21 33C20.1 27 27 21 36.3 19.7C45.6 18.4 53.8 22.2 54.6 28.2ZM74.8 13.4C78 18.5 74.2 26.8 66.2 31.7C58.2 36.7 49.2 36.6 45.9 31.4C42.7 26.3 46.6 18.1 54.5 13.1C62.5 8.1 71.6 8.2 74.8 13.4ZM30.4 142.5C27.5 147.5 18.5 147.8 10.4 143.1C2.3 138.4 -2 130.5 0.9 125.5C3.8 120.5 12.8 120.2 20.9 124.9C29 129.6 33.3 137.5 30.4 142.5ZM222.4 125.5C225.3 130.5 221 138.4 212.9 143.1C204.8 147.8 195.8 147.5 192.9 142.5C190 137.5 194.3 129.6 202.4 124.9C210.5 120.2 219.5 120.5 222.4 125.5ZM84.1 108C84.1 101.1 79.4 95.5 73.6 95.5C67.8 95.5 63.1 101.1 63.1 108C63.1 114.9 67.8 120.5 73.6 120.5C79.4 120.5 84.1 114.9 84.1 108ZM160.1 108C160.1 101.1 155.4 95.5 149.6 95.5C143.8 95.5 139.1 101.1 139.1 108C139.1 114.9 143.8 120.5 149.6 120.5C155.4 120.5 160.1 114.9 160.1 108ZM127.5 129.3C129.1 128 129.4 125.7 128.1 124C126.9 122.4 124.5 122.1 122.9 123.4C116.3 128.5 107 128.5 100.4 123.4C98.8 122.1 96.4 122.4 95.1 124C93.9 125.7 94.1 128 95.8 129.3C105.1 136.6 118.2 136.6 127.5 129.3Z";
/** 머리 위 골드 스터드. 몸보다 먼저 그려 아랫단을 머리 속에 묻는다. */
const GOLD_D =
  "M94.6 0L128.6 0C133.1 0 136.6 3.6 136.6 8L136.6 22C136.6 26.4 133.1 30 128.6 30L94.6 30C90.2 30 86.6 26.4 86.6 22L86.6 8C86.6 3.6 90.2 0 94.6 0Z";

/**
 * 분수 배치. 아래 물줄기·브릭은 이전 마크(스터드 윗면 가운데 138, 68.5) 좌표로 그렸다. 새 스터드
 * 윗면 가운데(111.6, 0)로 옮기고, 마크가 작아진 비율(223.3/270 ≈ 0.83)만큼 줄인다.
 */
const SPOUT_PLACE = "translate(111.6 0) scale(0.83) translate(-138 -68.5)";

/** 1×2 브릭 한 개 — 몸 34×17 위에 스터드 두 개. */
function SpoutBrick({ x, y, className }: { x: number; y: number; className: string }) {
  return (
    <g className={cn("gole-spout-brick", className)}>
      <rect x={x} y={y} width="34" height="17" rx="3" />
      <rect x={x + 6} y={y - 5} width="9" height="6" rx="2" />
      <rect x={x + 19} y={y - 5} width="9" height="6" rx="2" />
    </g>
  );
}

function BabyRoundArt({ spout }: { readonly spout: boolean }) {
  return (
    <>
      {spout ? (
        // 몸보다 먼저 그려, 떨어지는 브릭이 머리 뒤로 사라지게 한다.
        <g transform={SPOUT_PLACE}>
          <path
            d="M134 66C130 54 128 45 125 37M140 64C142 50 148 40 158 32M146 68C158 58 172 53 188 52"
            strokeWidth="4"
            strokeLinecap="round"
            opacity="0.75"
            className="gole-spout-stream stroke-brand-300"
          />
          <SpoutBrick x={150} y={24} className="gole-spout-brick-a fill-accent-500" />
          <SpoutBrick x={200} y={8} className="gole-spout-brick-b fill-brand-400" />
          <SpoutBrick x={222} y={52} className="gole-spout-brick-c fill-brand-300" />
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

export const babyRound: MascotArt = {
  viewBox: [0, 0, MARK_W, MARK_H],
  spoutViewBox: [0, -SPOUT_HEADROOM, MARK_W, MARK_H + SPOUT_HEADROOM],
  hasSpout: true,
  themable: true,
  Art: BabyRoundArt,
};
