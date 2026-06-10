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
        {/* 꼬리 플루크 (납작한 수평 고래 꼬리) */}
        <path
          d="M28.5 18.6C31.5 17.6 34 17 36.2 16.8C37.3 16.7 37.8 17.3 37.5 18.2C37.2 19.2 36.4 19.7 35.4 20.1C36.4 20.5 37.2 21 37.5 22C37.8 22.9 37.3 23.5 36.2 23.4C34 23.2 31.5 22.6 28.5 21.6Z"
          fill="#1a3fc0"
        />

        {/* 몸통 (둥근 사각형 = 레고 브릭 바디) */}
        <path
          d="M10 12H24C28 12 30 14 30.5 18C30.8 20 30.8 21 30.5 23C30 26.5 28 28.5 24 28.5H10C6 28.5 4.5 26 4.5 22.5V17.5C4.5 14 6 12 10 12Z"
          fill="#1d4ed8"
        />

        {/* 브릭 코스 심(seam) — 브릭으로 쌓은 느낌 */}
        <path d="M5.5 21H29.5" stroke="#1a3fc0" strokeWidth="0.9" strokeLinecap="round" opacity="0.4" />

        {/* 입(미소) */}
        <path
          d="M4.8 22.6C6 24 8 24.2 9.6 23.2"
          stroke="#1a3fc0"
          strokeWidth="0.9"
          strokeLinecap="round"
          fill="none"
        />

        {/* 분수(물줄기) — 머리 위 숨구멍 */}
        <g stroke="#eab308" strokeWidth="1.3" strokeLinecap="round" fill="none">
          <path d="M7.6 11.6C7.2 8.8 6.6 6.9 5.7 5.3" />
          <path d="M8.7 11.6C8.7 9 8.7 6.8 8.7 5" />
          <path d="M9.7 11.8C10.3 9 11.1 7 12 5.7" />
        </g>
        <circle cx="5.5" cy="4.9" r="0.7" fill="#eab308" />
        <circle cx="8.7" cy="4.6" r="0.7" fill="#eab308" />
        <circle cx="12.2" cy="5.4" r="0.7" fill="#eab308" />

        {/* 등 위 브릭 스터드 3개 (윗면 하이라이트로 입체) */}
        <ellipse cx="10.5" cy="12.1" rx="2.1" ry="1" fill="#3b5cf2" />
        <ellipse cx="10.5" cy="11.4" rx="2.1" ry="1" fill="#6082f7" />
        <ellipse cx="16" cy="12.1" rx="2.1" ry="1" fill="#3b5cf2" />
        <ellipse cx="16" cy="11.4" rx="2.1" ry="1" fill="#6082f7" />
        <ellipse cx="21.5" cy="12.1" rx="2.1" ry="1" fill="#3b5cf2" />
        <ellipse cx="21.5" cy="11.4" rx="2.1" ry="1" fill="#6082f7" />

        {/* 눈 */}
        <circle cx="8.6" cy="19.6" r="1.45" fill="#ffffff" />
        <circle cx="8.3" cy="19.4" r="0.6" fill="#1b2f66" />
      </svg>
      {showWordmark ? (
        <span>
          Go<span className="text-brand-600">Le</span>
        </span>
      ) : null}
    </span>
  );
}
