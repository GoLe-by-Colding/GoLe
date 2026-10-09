import { cn } from "@shared/lib";

export interface LogoProps {
  /** 고래 마크의 가로 픽셀 크기. 세로는 마크 비율(270:243.1)로 정해진다. */
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
 * viewBox는 그림 경계에 딱 맞춘다(꼬리 끝 y=0 ~ 지느러미 끝 y=243.1). 높이도 같은 비율로 계산하므로
 * preserveAspectRatio 레터박스로 빈 띠가 생기지 않는다. 분수 브릭도 이 상자 안(머리 오른쪽 위 빈자리)에
 * 앉혀 spout 변형이 같은 viewBox를 쓴다 — 애니메이션 중 떨어지는 브릭만 overflow로 보인다.
 */
const MARK_W = 270;
const MARK_H = 243.1;
/** 머리·꼬리·지느러미. 눈과 입은 반대 방향으로 감은 윤곽이라 nonzero 규칙에서 구멍이 된다. */
const BODY_D =
  "M48.7 66.2C45.2 53.3 17.2 45.4 6.6 42.5C3 41.6 0.4 38.4 0 34.7C-0.3 31 1.7 27.5 5.1 25.9C21.7 18.2 41.1 17.4 59.1 34.1C66.2 10.7 83.4 1.6 101.6 0C105.4 -0.3 108.9 1.8 110.5 5.1C112 8.5 111.3 12.5 108.7 15.2C100.9 22.9 80.6 43.8 84.1 56.8L102 123.8L66.6 133.3ZM38 189.5C38 125.7 82.5 89.6 138 89.6C193.5 89.6 237.9 125.7 237.9 189.5C237.9 220.1 218.5 239.5 187.9 239.5L88 239.5C57.5 239.5 38 220.1 38 189.5ZM51.9 215.9C45 227 33.9 238.1 20 242.3C11.7 245 4.7 240.9 6.1 232.5C8.9 218.7 24.2 204.8 43.6 200.6ZM232.3 200.6C251.8 204.8 267 218.7 269.8 232.5C271.2 240.9 264.3 245 255.9 242.3C242.1 238.1 231 227 224 215.9ZM82.5 159C82.5 168.2 89.9 175.6 99.1 175.6C108.3 175.6 115.8 168.2 115.8 159C115.8 149.8 108.3 142.3 99.1 142.3C89.9 142.3 82.5 149.8 82.5 159ZM160.2 159C160.2 168.2 167.6 175.6 176.8 175.6C186 175.6 193.5 168.2 193.5 159C193.5 149.8 186 142.3 176.8 142.3C167.6 142.3 160.2 149.8 160.2 159ZM77.5 205.2C93.4 221.5 115.2 230.6 138 230.6C160.7 230.6 182.5 221.5 198.4 205.2C200.4 203.3 200.3 200.1 198.4 198.2C196.4 196.2 193.2 196.3 191.3 198.2C177.3 212.5 158 220.6 138 220.6C117.9 220.6 98.7 212.5 84.6 198.2C83.4 197 81.6 196.5 79.8 196.9C78.1 197.3 76.7 198.7 76.3 200.4C75.8 202.1 76.3 204 77.5 205.2Z";
/** 머리 위 골드 스터드. 몸보다 먼저 그려 아랫단을 머리 속에 묻는다. */
const GOLD_D =
  "M116.3 68.5L159.6 68.5C164.5 68.5 168.5 72.5 168.5 77.4L168.5 97.4C168.5 102.3 164.5 106.3 159.6 106.3L116.3 106.3C111.4 106.3 107.4 102.3 107.4 97.4L107.4 77.4C107.4 72.5 111.4 68.5 116.3 68.5Z";

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

/**
 * GoLe 고래 — 정면을 보는 둥근 고래, 머리 위에 골드 스터드 하나, 머리 뒤로 솟은 꼬리.
 *
 * <p>설계 원칙: 레고 미니피규어 머리처럼 정수리에 스터드가 하나 있다. 브릭다움은 이 스터드가,
 * 고래다움은 머리 뒤 꼬리(플루크)·넓은 입선·옆 지느러미가 맡는다. 옆모습 고래 등에 네모를 얹는
 * 구도(Docker 로고와 닮은 구도)를 쓰지 않는다. 16px에서는 실루엣·두 눈·골드 한 점이 남는다.
 *
 * <p>색은 두 가지 + 골드 한 점이다. 몸·골드는 테마 토큰(brand-600·accent-500)을 따르고,
 * 어두운 배경에서는 호출부가 `[--gole-mark-body:...]`로 몸 색만 바꿀 수 있다(예: 푸터의 흰 고래).
 * 눈과 입은 구멍이라 배경색이 그대로 비친다.
 *
 * <p>스터드가 곧 분수공이다. spout 변형은 스터드에서 물줄기와 브릭이 솟아 꼬리 반대쪽(오른쪽)으로
 * 떨어진다. `gole-spout-*` 키프레임이 브릭을 오른쪽 아래로 떨어뜨리므로 그룹을 뒤집지 않는다.
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
          // 몸보다 먼저 그려, 떨어지는 브릭이 머리 뒤로 사라지게 한다.
          <>
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
          </>
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
