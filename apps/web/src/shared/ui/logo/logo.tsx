import { cn } from "@shared/lib";

export interface LogoProps {
  /** 고래 마크의 가로 픽셀 크기. 세로는 마크 비율(223.3:172.3)로 정해진다. */
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
 * viewBox는 그림 경계에 딱 맞춘다(스터드 윗면 y=0 ~ 배 아래 y=172.3). 높이도 같은 비율로 계산하므로
 * preserveAspectRatio 레터박스로 빈 띠가 생기지 않는다. 스터드가 맨 위라 상자 안에 분수 자리가 없으므로,
 * spout 변형만 viewBox를 위로 SPOUT_HEADROOM만큼 넓혀 물줄기·브릭을 상자 안에 둔다.
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

/**
 * GoLe 고래 — 정면을 보는 통통한 아기 고래, 정수리에 골드 스터드 하나, 머리 뒤로 살짝 나온 짧은 꼬리.
 *
 * <p>설계 원칙: 레고 미니피규어 머리처럼 정수리에 스터드가 하나 있다. 브릭다움은 이 스터드가,
 * 고래다움은 머리 뒤 둥근 플루크·옆 지느러미가 맡는다. 귀여움은 비율에서 나온다 — 매끈한 돔과
 * 볼살 같은 아랫단, 아래쪽에 넓게 벌어진 작은 타원 눈, 눈 사이 아래의 작은 미소. 큰 흰 눈·얼굴을
 * 가로지르는 입·뾰족한 꼬리는 무섭게 읽혀서(2026-10-09 사용자 피드백) 쓰지 않는다. 옆모습 고래 등에
 * 네모를 얹는 구도(Docker 로고와 닮은 구도)도 쓰지 않는다. 16px에서는 실루엣·두 눈·골드 한 점이 남는다.
 *
 * <p>색은 두 가지 + 골드 한 점이다. 몸·골드는 테마 토큰(brand-600·accent-500)을 따르고,
 * 어두운 배경에서는 호출부가 `[--gole-mark-body:...]`로 몸 색만 바꿀 수 있다(예: 푸터의 흰 고래).
 * 눈과 입은 구멍이라 배경색이 그대로 비친다.
 *
 * <p>스터드가 곧 분수공이다. spout 변형은 스터드에서 물줄기와 브릭이 솟아 꼬리 반대쪽(오른쪽)으로
 * 떨어진다. `gole-spout-*` 키프레임이 브릭을 오른쪽 아래로 떨어뜨리므로 그룹을 뒤집지 않는다.
 * 분수 도형은 이전 마크의 좌표로 그려 두고 SPOUT_PLACE 한 번으로 새 스터드 윗면 가운데에 옮긴다 —
 * 키프레임이 `transform-box: fill-box`라 축척을 바꿔도 움직임 비율이 그대로다.
 */
export function Logo({
  size = 32,
  showWordmark = true,
  className,
  accentClassName = "text-brand-600",
  spout = false,
}: LogoProps) {
  const headroom = spout ? SPOUT_HEADROOM : 0;
  return (
    <span
      className={cn("inline-flex items-center gap-1.5 font-extrabold tracking-tight", className)}
    >
      <svg
        width={size}
        height={Math.round((size * (MARK_H + headroom)) / MARK_W)}
        viewBox={`0 ${-headroom} ${MARK_W} ${MARK_H + headroom}`}
        fill="none"
        aria-hidden="true"
        className="shrink-0 overflow-visible"
      >
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
      </svg>
      {showWordmark ? (
        <span>
          Go<span className={accentClassName}>Le</span>
        </span>
      ) : null}
    </span>
  );
}
