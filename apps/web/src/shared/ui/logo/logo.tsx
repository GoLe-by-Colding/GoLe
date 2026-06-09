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
        {/* 고래 실루엣 (브랜드 블루) */}
        <path
          d="M8 26c-1-3 0-7 4-10s9-4 13-3c4 1 7 4 9 7 1 2 1 4-1 5s-5 1-8 0c-2-1-4-1-6 0s-4 2-6 2-4-1-5-1z"
          fill="#1D4ED8"
        />
        {/* 브릭 스터드 (고래 등 위) */}
        <circle cx="18" cy="14" r="2.5" fill="#3B5CF2" />
        <circle cx="24" cy="13" r="2.5" fill="#3B5CF2" />
        {/* 꼬리 */}
        <path d="M6 25c-2 0-3-2-2-4s2-3 3-2" fill="#1D4ED8" />
        {/* 분수 (accent gold) */}
        <path
          d="M28 9c0-3 1-5 2-6 0 2 1 3 2 4-1-2 0-4 1-5 0 2 1 4 0 6"
          stroke="#EAB308"
          strokeWidth="1.2"
          strokeLinecap="round"
          fill="none"
        />
      </svg>
      {showWordmark ? (
        <span>
          Go<span className="text-brand-600">Le</span>
        </span>
      ) : null}
    </span>
  );
}
