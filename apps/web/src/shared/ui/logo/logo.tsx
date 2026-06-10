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
 * 한 획의 고래 실루엣(평평한 브릭 등 + 클래식 플루크) 위에 스터드 3개,
 * 가운데 스터드만 브릭 골드인 것이 시그니처. 눈은 네거티브 스페이스.
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
        {/* 등 위 스터드 3개 — 가운데 골드가 시그니처. 몸통보다 먼저 그려 하단이 등에 묻힌다. */}
        <rect x="9.5" y="10.5" width="5" height="3.2" rx="1.3" fill="#1D4ED8" />
        <rect x="15.55" y="10.5" width="5" height="3.2" rx="1.3" fill="#EAB308" />
        <rect x="21.6" y="10.5" width="5" height="3.2" rx="1.3" fill="#1D4ED8" />

        {/* 몸통 — 평평한 브릭 등(직선) + 클래식 고래 플루크, 한 획 실루엣 */}
        <path
          d="M9 13
             L26.3 13
             C28.4 13 29.4 14.9 29.8 17.4
             C30.8 16 32.6 14.8 35 14.4
             C36.4 14.2 37.2 15.6 36.4 16.8
             C35.4 18.3 34.2 19.4 32.8 20.1
             C34.3 20.9 35.6 22.1 36.6 23.7
             C37.4 24.9 36.5 26.3 35.1 26
             C32.7 25.5 30.8 24.2 29.7 22.4
             C28.9 25.6 26.4 28.8 21.8 28.8
             L13 28.8
             C7.5 28.8 3.7 25.2 3.7 20.6
             C3.7 16.5 5.7 13.4 9 13
             Z"
          fill="#1D4ED8"
        />

        {/* 눈 — 네거티브 스페이스 */}
        <circle cx="9.7" cy="19.7" r="1.55" fill="#ffffff" />
      </svg>
      {showWordmark ? (
        <span>
          Go<span className={accentClassName}>Le</span>
        </span>
      ) : null}
    </span>
  );
}
