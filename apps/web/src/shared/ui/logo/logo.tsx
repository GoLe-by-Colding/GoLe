import { cn } from "@shared/lib";

export interface LogoProps {
  /** 고래 마크의 가로 픽셀 크기. 세로는 마크 비율(269:198.3)로 정해진다. */
  readonly size?: number;
  readonly showWordmark?: boolean;
  readonly className?: string;
  readonly accentClassName?: string;
  /** 히어로처럼 큰 장면에서만 브릭 분수를 표시한다. */
  readonly spout?: boolean;
}

/*
 * 정본은 같은 폴더의 `mark.svg`다. 아래 두 경로는 그 파일에서 그대로 복사했고,
 * `scripts/build-brand-icons.mjs`가 문자열이 같은지 검사한다. 마크를 고치면 mark.svg를 먼저 고친다.
 *
 * viewBox는 그림 경계에 딱 맞춘다(꼬리 끝 y=0 ~ 배 y=198.3). 높이도 같은 비율로 계산하므로
 * preserveAspectRatio 레터박스로 빈 띠가 생기지 않는다. 분수 브릭도 이 상자 안(꼬리 끝보다 아래)에
 * 앉혀 spout 변형이 같은 viewBox를 쓴다 — 애니메이션 중 잠깐 위로 솟는 8단위만 overflow로 보인다.
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

/**
 * GoLe 브릭 고래 — 꼬리를 든 고래 한 마리, 평평한 등에서 솟은 스터드 셋, 가운데만 골드.
 *
 * <p>설계 원칙: 16px에서도 고래로 읽혀야 하므로 면광·힌지·입 같은 디테일 없이 단색 실루엣
 * 하나로 그린다. 고래다움은 들어 올린 꼬리(플루크)와 반원 머리가, 브릭다움은 수평인 등과
 * 그 윤곽에서 솟은 스터드가 맡는다. 스터드는 몸과 같은 path에 합쳐 스티커처럼 떠 보이지 않게 한다.
 *
 * <p>색은 두 가지 + 골드 한 점이다. 몸·골드는 테마 토큰(brand-600·accent-500)을 따르고,
 * 어두운 배경에서는 호출부가 `[--gole-mark-body:...]`로 몸 색만 바꿀 수 있다(예: 푸터의 흰 고래).
 * 눈은 구멍이라 배경색이 그대로 비친다.
 *
 * <p>고래는 워드마크 쪽(오른쪽)을 본다. 분수공은 머리 꼭대기이고, 브릭은 등 너머 꼬리 쪽으로
 * 떨어져야 하므로 분수 그룹을 분수공 축(x=224)으로 뒤집어 `gole-spout-*` 키프레임을 그대로 쓴다.
 */
export function Logo({
  size = 32,
  showWordmark = true,
  className,
  accentClassName = "text-brand-600",
  spout = false,
}: LogoProps) {
  return (
    <span
      className={cn("inline-flex items-center gap-1.5 font-extrabold tracking-tight", className)}
    >
      <svg
        width={size}
        height={Math.round((size * MARK_H) / MARK_W)}
        viewBox={`0 0 ${MARK_W} ${MARK_H}`}
        fill="none"
        aria-hidden="true"
        className="shrink-0 overflow-visible"
      >
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
      </svg>
      {showWordmark ? (
        <span>
          Go<span className={accentClassName}>Le</span>
        </span>
      ) : null}
    </span>
  );
}
