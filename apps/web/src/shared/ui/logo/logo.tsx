import { cn } from "@shared/lib";

export interface LogoProps {
  /** 고래 마크 픽셀 크기. 기본 32. */
  readonly size?: number;
  /** "GoLe" 워드마크 표시 여부. 기본 true. */
  readonly showWordmark?: boolean;
  /** 래퍼 추가 클래스. 워드마크 색/크기는 여기서 text-* 로 제어한다. */
  readonly className?: string;
}

/**
 * GoLe 브랜드 로고 — 고래 실루엣 + 브릭 스터드 + 분수(accent gold) 마크와
 * "Go<brand>Le</brand>" 워드마크 락업. 헤더·푸터·인증 화면 등에서 공통 사용한다.
 *
 * 워드마크의 기본 글자색/크기는 부모의 text-* 유틸리티를 상속하므로,
 * 호출부에서 className 으로 색/크기를 지정한다. (예: text-lg text-neutral-900)
 */
export function Logo({ size = 32, showWordmark = true, className }: LogoProps) {
  return (
    <span className={cn("inline-flex items-center gap-1.5 font-extrabold tracking-tight", className)}>
      <svg
        width={size}
        height={size}
        viewBox="0 0 40 40"
        fill="none"
        aria-hidden="true"
        className="shrink-0"
      >
        {/* 분수(물줄기) — 등 위 스터드에서 솟는 골드 워터젯 */}
        <g stroke="#eab308" strokeWidth="1.35" strokeLinecap="round" fill="none">
          <path d="M22.4 9.2C22.1 6.7 21.3 5 20 3.9" />
          <path d="M23.4 8.9C23.5 6.8 23.6 5.5 23.6 4.3" />
          <path d="M24.2 9.2C24.8 7 25.7 5.6 26.8 4.7" />
        </g>
        <circle cx="19.8" cy="3.5" r="0.75" fill="#eab308" />
        <circle cx="23.6" cy="3.9" r="0.75" fill="#eab308" />
        <circle cx="27.1" cy="4.3" r="0.75" fill="#eab308" />

        {/* 꼬리 지느러미 */}
        <path
          d="M30.5 20.5C33.4 17.6 37 15.8 38.8 16.4C38.2 19 38.2 21.2 36.2 22.7C38.2 24.7 38.3 27.2 38.8 29.4C36 28.6 32.6 25.8 30.6 23.2Z"
          fill="#1a3fc0"
        />

        {/* 몸통 */}
        <path
          d="M4.5 23C4.5 16.4 10.2 12.2 17.4 12.2C24.6 12.2 30.8 15.4 32.2 21.6C32.8 24.3 31.1 27.2 25.8 28.8C18.4 31 4.5 30.3 4.5 23Z"
          fill="#1d4ed8"
        />

        {/* 가슴 지느러미 */}
        <path
          d="M13.5 27.4C15.2 31 18.9 32.1 21.6 30.4C19.9 28.3 16.7 27.2 13.5 27.4Z"
          fill="#1a3fc0"
        />

        {/* 등 위 브릭 스터드 */}
        <ellipse cx="16.8" cy="11.6" rx="2.2" ry="1.9" fill="#3b5cf2" />
        <ellipse cx="22.6" cy="10.9" rx="2.2" ry="1.9" fill="#3b5cf2" />
        <ellipse cx="16.8" cy="11.1" rx="2.2" ry="1.1" fill="#6082f7" />
        <ellipse cx="22.6" cy="10.4" rx="2.2" ry="1.1" fill="#6082f7" />

        {/* 눈 */}
        <circle cx="10.8" cy="21.6" r="1.45" fill="#ffffff" />
        <circle cx="10.5" cy="21.3" r="0.55" fill="#1b2f66" />
      </svg>
      {showWordmark ? (
        <span>
          Go<span className="text-brand-600">Le</span>
        </span>
      ) : null}
    </span>
  );
}
