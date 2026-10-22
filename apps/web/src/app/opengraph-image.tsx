import { ImageResponse } from "next/og";

/**
 * 전역 OG/트위터 카드 이미지(런타임 PNG 생성). Next가 og:image·twitter:image 메타를
 * 자동 연결한다. satori 기본 폰트가 한글을 포함하지 않으므로 텍스트는 영문으로 구성한다.
 */
export const alt = "GoLe — Brick Marketplace";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

/*
 * 고래 마크 경로는 `shared/ui/logo/mark.svg`에서 그대로 복사했다. OG 라우트는 런타임에 파일을
 * 읽지 않도록 문자열로 들고 있고, `scripts/build-brand-icons.mjs`가 정본과 같은지 검사한다.
 */
const MARK_BODY_D =
  "M48 14H146C162.6 14 176 27.4 176 44V76C176 101.4 155.4 122 130 122H48C21.5 122 0 100.5 0 74V62C0 35.5 21.5 14 48 14ZM57 0H75C78.3 0 81 2.7 81 6V22H51V6C51 2.7 53.7 0 57 0ZM135 0H153C156.3 0 159 2.7 159 6V22H129V6C129 2.7 131.7 0 135 0ZM163 63C181 66 185 53 194 40C202 28 215 22 229 22C233 22 235.8 26 234 30L226 49C221 61 212 68 200 72C213 72 226 78 231 89C233 94 230 99 225 99H211C195 99 181 92 174 81H163V63ZM87 105C96 104 108 108 115 118C119 123 116 129 111 131C99 132.2 88 126 82 118C78 113 81 107 87 105Z";
const MARK_TOP_D =
  "M6 39C14.4 23.9 30.4 14 48 14H146C159 14 170.2 22.3 174.3 34L176 41H12C8 41 6 40 6 39ZM57 0H75C78.3 0 81 2.7 81 6V22H51V6C51 2.7 53.7 0 57 0ZM135 0H153C156.3 0 159 2.7 159 6V22H129V6C129 2.7 131.7 0 135 0Z";
const MARK_GOLD_D = "M94 0H116C119.3 0 122 2.7 122 6V22H88V6C88 2.7 90.7 0 94 0Z";
const MARK_FACE_D =
  "M48 53C40.8 53 35 58.8 35 66C35 73.2 40.8 79 48 79C55.2 79 61 73.2 61 66C61 58.8 55.2 53 48 53ZM44 88C45.7 86.5 45.8 84 44.3 82.4C42.8 80.8 40.3 80.6 38.7 82.1C35.1 85.3 29.7 85.3 26.1 82.1C24.5 80.6 22 80.8 20.5 82.4C19 84 19.1 86.5 20.8 88C27.5 94 37.3 94 44 88Z";
const MARK_GLINT_D =
  "M49 55C52.9 55 56 58.1 56 62C56 65.9 52.9 69 49 69C45.1 69 42 65.9 42 62C42 58.1 45.1 55 49 55Z";

export default function OpengraphImage() {
  return new ImageResponse(
    // Satori(`next/og`)는 실험 기능인 `tw` prop으로 Tailwind 클래스를 읽는다. 앱의 `@theme`은 모르므로 색은 임의 값으로 쓴다.
    // 브랜드 규칙: 단색 면. 그라데이션 배경은 쓰지 않는다.
    // Satori `tw`에는 gap 유틸리티가 없어 간격은 두 번째 자식의 margin으로 준다(렌더 픽셀 동일 확인).
    <div tw="flex h-full w-full items-stretch justify-between bg-[#1d4ed8] p-[80px] font-sans text-white">
      <div tw="flex flex-col justify-between">
        <div tw="flex text-[46px] font-extrabold tracking-[-1px]">
          Go<span tw="text-[#facc15]">Le</span>
        </div>

        <div tw="flex flex-col">
          <div tw="flex flex-col text-[92px] font-extrabold leading-[1.02] tracking-[-4px]">
            <span>Brick</span>
            <span>Marketplace</span>
          </div>
          <div tw="mt-[18px] text-[32px] opacity-85">
            Prices · Direct Chat · Collection · Community
          </div>
        </div>

        <div tw="text-[28px] opacity-70">gole.co.kr</div>
      </div>

      {/* 흰 옆모습 브릭 고래 + 골드 스터드 한 점 — 앱 아이콘과 같은 조합. 힌지 핀 고리로 파란 면이 비친다. */}
      <div tw="flex items-center pb-[24px]">
        <svg width="420" height="235" viewBox="0 0 235.8 132.2" fill="none">
          <path d={MARK_GOLD_D} fill="#facc15" />
          <path d={MARK_BODY_D} fill="#ffffff" />
          <path d={MARK_TOP_D} fill="#ffffff" />
          <path d={MARK_FACE_D} fill="#131e4f" />
          <path d={MARK_GLINT_D} fill="#ffffff" />
        </svg>
      </div>
    </div>,
    size,
  );
}
