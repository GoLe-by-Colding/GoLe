import { cn } from "@shared/lib";

export interface LogoProps {
  /** 고래 마크 픽셀 크기. 기본 32. */
  readonly size?: number;
  /** "GoLe" 워드마크 표시 여부. 기본 true. */
  readonly showWordmark?: boolean;
  /** 래퍼 추가 클래스. 워드마크 색/크기는 여기서 text-* 로 제어한다. */
  readonly className?: string;
  /** "Le" 포인트 글자색. 기본 brand-600, 다크 배경에선 text-accent-400 권장. */
  readonly accentClassName?: string;
}

/**
 * GoLe 브랜드 마크 — "브릭 고래".
 *
 * 볼록한 둥근 이마 돔 + 통통한 몸 + 큰 눈 + 미소의 마스코트 실루엣 위에 스터드 3개,
 * 가운데 스터드만 브릭 골드인 것이 시그니처. 눈/입은 네거티브 스페이스.
 * 플랫 단색 지오메트리라 16px 파비콘부터 히어로 워터마크까지 동일하게 스케일된다.
 *
 * 워드마크의 기본 글자색/크기는 부모의 text-* 유틸리티를 상속하므로,
 * 호출부에서 className 으로 색/크기를 지정한다. (예: text-lg text-neutral-900)
 */
export function Logo({
  size = 32,
  showWordmark = true,
  className,
  accentClassName = "text-brand-600",
}: LogoProps) {
  return (
    <span
      className={cn("inline-flex items-center gap-1.5 font-extrabold tracking-tight", className)}
    >
      <svg
        width={size}
        height={size}
        viewBox="0 0 40 40"
        fill="none"
        aria-hidden="true"
        className="shrink-0"
      >
        {/* 정수리 스터드 3개 — 가운데 골드가 시그니처. 몸통보다 먼저 그려 하단이 머리에 묻힌다. */}
        <rect x="7.6" y="5" width="3.2" height="3" rx="1.2" fill="#1D4ED8" />
        <rect x="11.5" y="4.6" width="3.2" height="3" rx="1.2" fill="#EAB308" />
        <rect x="15.4" y="5" width="3.2" height="3" rx="1.2" fill="#1D4ED8" />

        {/* 몸통 — 볼록한 둥근 이마 돔 → 통통한 배 → 위로 올라간 플루크 */}
        <path
          d="M8 7.4
             C5 8.2 3 11.4 2.8 16
             C2.7 17.8 2.9 19.2 3.4 20.8
             C5 26.2 9.8 30.2 15.6 30.2
             C21.4 30.2 25.8 26.4 27 21.9
             C28.2 23.7 30.1 25 32.5 25.5
             C33.9 25.8 34.8 24.4 34 23.2
             C33 21.6 31.7 20.4 30.2 19.6
             C31.7 18.9 33 17.8 34 16.3
             C34.8 15.1 33.9 13.7 32.5 14
             C30.1 14.5 28.2 15.8 27 17.6
             C25.9 13.4 22.6 8.4 16.8 7.4
             C13.8 6.9 10.8 6.6 8 7.4
             Z"
          fill="#1D4ED8"
        />

        {/* 큰 눈 — 네거티브 스페이스 */}
        <circle cx="9" cy="17.6" r="2" fill="#ffffff" />
        {/* 미소 */}
        <path
          d="M5.2 21 Q7.3 23 10 21.8"
          stroke="#ffffff"
          strokeWidth="1.2"
          strokeLinecap="round"
          fill="none"
          opacity="0.65"
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
