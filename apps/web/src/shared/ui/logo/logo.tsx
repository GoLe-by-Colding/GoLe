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
        {/* 꼬리 플루크 (위로 뻗는 고래 꼬리) */}
        <path
          d="M30 20.5C32.5 19 34.5 16.5 36 15C35.6 17.5 35 19.3 34.2 20.8C36 21 37.6 22.2 39 24C36.5 24.2 34 23.2 31.8 21.6C31.2 22 30.6 22.2 30 22Z"
          fill="#1a3fc0"
        />

        {/* 몸통 (납작한 등 = 브릭 윗면) */}
        <path
          d="M5 21C5 15.5 9.5 13 16 13L26.5 13C30 13 32.5 15.5 32.5 19.5C32.5 24.5 28 28.5 19.5 28.5C10 28.5 5 26 5 21Z"
          fill="#1d4ed8"
        />

        {/* 가슴 지느러미 */}
        <path
          d="M14.5 27C16 30.5 19.5 31.5 22 30C20.3 28 17.5 27 14.5 27Z"
          fill="#1a3fc0"
        />

        {/* 브릭 코스 심(seam) */}
        <path d="M6.5 20.6H30" stroke="#1a3fc0" strokeWidth="0.8" strokeLinecap="round" opacity="0.4" />

        {/* 입(미소) */}
        <path
          d="M5 22.4C6.6 23.9 8.7 23.9 10.2 22.8"
          stroke="#1a3fc0"
          strokeWidth="0.9"
          strokeLinecap="round"
          fill="none"
        />

        {/* 분수(물줄기) — 머리 위 숨구멍에서 솟는 골드 워터젯 */}
        <g stroke="#eab308" strokeWidth="1.3" strokeLinecap="round" fill="none">
          <path d="M9.6 12.4C9.2 9.4 8.6 7.4 7.6 5.8" />
          <path d="M10.7 12.4C10.7 9.6 10.7 7.4 10.7 5.6" />
          <path d="M11.7 12.6C12.3 9.8 13.1 7.8 14 6.4" />
        </g>
        <circle cx="7.4" cy="5.4" r="0.7" fill="#eab308" />
        <circle cx="10.7" cy="5.2" r="0.7" fill="#eab308" />
        <circle cx="14.2" cy="6" r="0.7" fill="#eab308" />

        {/* 등 위 브릭 스터드 3개 (윗면 하이라이트로 입체) */}
        <ellipse cx="13" cy="12.3" rx="2.1" ry="1" fill="#3b5cf2" />
        <ellipse cx="13" cy="11.6" rx="2.1" ry="1" fill="#6082f7" />
        <ellipse cx="18.5" cy="12.3" rx="2.1" ry="1" fill="#3b5cf2" />
        <ellipse cx="18.5" cy="11.6" rx="2.1" ry="1" fill="#6082f7" />
        <ellipse cx="24" cy="12.3" rx="2.1" ry="1" fill="#3b5cf2" />
        <ellipse cx="24" cy="11.6" rx="2.1" ry="1" fill="#6082f7" />

        {/* 눈 */}
        <circle cx="9.5" cy="19.8" r="1.45" fill="#ffffff" />
        <circle cx="9.2" cy="19.6" r="0.6" fill="#1b2f66" />
      </svg>
      {showWordmark ? (
        <span>
          Go<span className="text-brand-600">Le</span>
        </span>
      ) : null}
    </span>
  );
}
