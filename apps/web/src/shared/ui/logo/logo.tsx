import { cn } from "@shared/lib";

export interface LogoProps {
  /** 고래 마크의 가로 픽셀 크기. 세로는 마크 비율(235.8:132.2)로 정해진다. */
  readonly size?: number;
  readonly showWordmark?: boolean;
  readonly className?: string;
  readonly accentClassName?: string;
  /** 히어로처럼 큰 장면에서만 브릭 분수를 표시한다. */
  readonly spout?: boolean;
}

/*
 * 정본은 같은 폴더의 `mark.svg`다. 아래 다섯 경로는 그 파일에서 그대로 복사했고,
 * `scripts/build-brand-icons.mjs`가 문자열이 같은지 검사한다. 마크를 고치면 mark.svg를 먼저 고친다.
 *
 * viewBox는 그림 경계에 딱 맞춘다(스터드 윗면 y=0 ~ 지느러미 끝 y=132.2). 높이도 같은 비율로 계산하므로
 * preserveAspectRatio 레터박스로 빈 띠가 생기지 않는다. 스터드가 맨 위라 상자 안에 분수 자리가 없으므로,
 * spout 변형만 viewBox를 위로 SPOUT_HEADROOM만큼 넓혀 물줄기·브릭을 상자 안에 둔다.
 */
const MARK_W = 235.8;
const MARK_H = 132.2;
/** spout 변형에서 스터드 위로 물줄기·브릭이 올라갈 자리(마크 단위). */
const SPOUT_HEADROOM = 40;
/**
 * 몸 실루엣 — 브릭 몸, 파란 스터드 2개, 꼬리 받침, V 꼬리 플레이트 2장, 지느러미.
 * 꼬리 힌지 핀 고리는 반대 방향으로 감아 nonzero 규칙에서 구멍이 된다.
 */
const BODY_D =
  "M48 11L158 11C167.9 11 176 19.1 176 29L176 77C176 98 159 115 138 115L40 115C17.9 115 0 97.1 0 75L0 59C0 32.5 21.5 11 48 11ZM61 0L79 0C81.2 0 83 1.8 83 4L83 17L57 17L57 4C57 1.8 58.8 0 61 0ZM133 0L151 0C153.2 0 155 1.8 155 4L155 17L129 17L129 4C129 1.8 130.8 0 133 0ZM179 52L193 52C197.4 52 201 55.6 201 60L201 78C201 82.4 197.4 86 193 86L179 86C174.6 86 171 82.4 171 78L171 60C171 55.6 174.6 52 179 52ZM192.9 44.1L214.1 22.9C218.8 18.2 226.4 18.2 231.1 22.9C235.8 27.6 235.8 35.2 231.1 39.9L209.9 61.1C205.2 65.8 197.6 65.8 192.9 61.1C188.2 56.4 188.2 48.8 192.9 44.1ZM208.6 76.8L226.7 85.3C232.2 87.8 234.6 94.4 232 99.9C229.5 105.4 222.9 107.8 217.4 105.2L199.3 96.7C193.8 94.2 191.4 87.6 194 82.1C196.5 76.6 203.1 74.2 208.6 76.8ZM86.5 105.7L108.3 115.8C112.3 117.7 114 122.4 112.1 126.5C110.3 130.5 105.5 132.2 101.5 130.3L79.7 120.2C75.7 118.3 74 113.6 75.9 109.5C77.7 105.5 82.5 103.8 86.5 105.7ZM192 62C188.1 62 185 65.1 185 69C185 72.9 188.1 76 192 76C195.9 76 199 72.9 199 69C199 65.1 195.9 62 192 62ZM192 64.6C194.4 64.6 196.4 66.6 196.4 69C196.4 71.4 194.4 73.4 192 73.4C189.6 73.4 187.6 71.4 187.6 69C187.6 66.6 189.6 64.6 192 64.6Z";
/** 몸 브릭의 밝은 윗단과 파란 스터드 2개 — 원본의 "윗면" 톤이다. */
const TOP_D =
  "M4.4 39C12.2 21.9 29.2 11 48 11L158 11C167.9 11 176 19.1 176 29L176 39L4.4 39ZM61 0L79 0C81.2 0 83 1.8 83 4L83 17L57 17L57 4C57 1.8 58.8 0 61 0ZM133 0L151 0C153.2 0 155 1.8 155 4L155 17L129 17L129 4C129 1.8 130.8 0 133 0Z";
/** 가운데 골드 스터드. 몸보다 먼저 그려 아랫단을 몸 속에 묻는다. */
const GOLD_D = "M97 0L115 0C117.2 0 119 1.8 119 4L119 17L93 17L93 4C93 1.8 94.8 0 97 0Z";
/** 눈·미소. 몸과 반대 방향으로 감아 두어, 단색 변형은 몸 경로 뒤에 이어 붙이기만 해도 구멍이 된다. */
const FACE_D =
  "M50 55C45 55 41 59.5 41 65C41 70.5 45 75 50 75C55 75 59 70.5 59 65C59 59.5 55 55 50 55ZM41.9 84.2C42.7 83.2 42.6 81.8 41.6 81C40.7 80.2 39.3 80.3 38.5 81.3C36.6 83.5 33.9 84.8 31 84.8C28.1 84.8 25.4 83.5 23.5 81.3C22.7 80.3 21.3 80.2 20.4 81C19.4 81.8 19.3 83.2 20.1 84.2C22.8 87.4 26.8 89.2 31 89.2C35.2 89.2 39.2 87.4 41.9 84.2Z";
/** 눈 반짝임. */
const GLINT_D =
  "M53 58C54.7 58 56 59.3 56 61C56 62.7 54.7 64 53 64C51.3 64 50 62.7 50 61C50 59.3 51.3 58 53 58Z";

/**
 * 분수 배치. 아래 물줄기·브릭은 원본 옆모습 마크(9f581c70)의 좌표 그대로다 — 가운데 스터드 윗면 가운데가
 * (111, -5)였다. 새 골드 스터드 윗면 가운데(106, 0)로 옮기고, 마크가 작아진 비율(약 0.88)만큼 줄인다.
 */
const SPOUT_PLACE = "translate(106 0) scale(0.88) translate(-111 5)";

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
 * GoLe 고래 — 옆모습 브릭 고래. 얼굴은 왼쪽, 꼬리는 오른쪽이고, 몸은 실제로 조립한 장난감 브릭처럼 읽힌다.
 *
 * <p>설계 원칙은 원본(9f581c70)의 문법을 잇는다. 매끈한 고래 위에 스터드를 "얹지" 않는다 — 스터드는 몸 브릭의
 * 윗단에서 솟은 같은 실루엣이고, 윗단은 한 톤 밝은 면이라 사출 브릭처럼 면 단위로 빛을 받는다. 꼬리는 받침 블록에
 * 힌지 핀으로 물린 V 플레이트 두 장, 지느러미는 배 아래 플레이트 한 장이라 "붙였다"가 아니라 "끼웠다"로 읽힌다.
 *
 * <p>친근함은 둥근 끝에서 나온다 — 큰 이마 반경, 둥근 턱·배, 끝이 둥근 꼬리·지느러미, 까만 눈과 작은 반짝임,
 * 작은 미소. 파랑은 원본의 5~6톤을 2톤(몸·윗단)으로 줄였다. 2026-10-09 하루 동안 쓴 정면 아기 고래는 사용자
 * 의도가 아니어서 폐기했다(`brand-icon` 스펙). 마크는 옆모습이다.
 *
 * <p>색: 몸 brand-600, 윗단 brand-400, 골드 스터드 accent-500, 눈·입 brand-950, 반짝임 흰색. 어두운 배경에서는
 * 호출부가 `[--gole-mark-body:...]`로 몸 색을 바꾸면 윗단도 따라간다(`--gole-mark-top`을 따로 주지 않는 한).
 * 힌지 핀 고리는 구멍이라 배경색이 비친다. 윗단은 톤으로만 나눈다 — 흰 이음선은 작은 크기에서 뚜껑 선처럼 읽혔다.
 *
 * <p>가운데 골드 스터드가 분수공이다. spout 변형은 거기서 물줄기와 브릭이 솟아 꼬리 쪽(오른쪽)으로 떨어진다.
 * `gole-spout-*` 키프레임은 원본 때 그대로이고 `transform-box: fill-box`라 축척을 바꿔도 움직임 비율이 같다.
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
          // 몸보다 먼저 그려, 떨어지는 브릭이 몸 뒤로 사라지게 한다.
          <g transform={SPOUT_PLACE}>
            <path
              d="M96 3C90-13 84-21 75-28M100 1C102-16 108-28 118-36M104 4C116-9 130-15 144-15"
              strokeWidth="4"
              strokeLinecap="round"
              opacity="0.75"
              className="gole-spout-stream stroke-brand-300"
            />
            <SpoutBrick x={60} y={-30} className="gole-spout-brick-a fill-accent-500" />
            <SpoutBrick x={114} y={-42} className="gole-spout-brick-b fill-brand-400" />
            <SpoutBrick x={154} y={-20} className="gole-spout-brick-c fill-brand-300" />
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
        <path
          d={FACE_D}
          fill="#131E4F"
          className="fill-[var(--gole-mark-face,var(--color-brand-950))]"
        />
        <path d={GLINT_D} fill="#FFFFFF" className="fill-[var(--gole-mark-glint,#fff)]" />
      </svg>
      {showWordmark ? (
        <span>
          Go<span className={accentClassName}>Le</span>
        </span>
      ) : null}
    </span>
  );
}
