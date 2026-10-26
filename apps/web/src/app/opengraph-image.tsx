import { ImageResponse } from "next/og";
import { ASSEMBLED_MARK_VIEW_BOX, AssembledMark } from "@shared/ui";

/**
 * 전역 OG/트위터 카드 이미지(런타임 PNG 생성). Next가 og:image·twitter:image 메타를
 * 자동 연결한다. satori 기본 폰트가 한글을 포함하지 않으므로 텍스트는 영문으로 구성한다.
 */
export const alt = "GoLe — Brick Marketplace";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpengraphImage() {
  return new ImageResponse(
    // Satori(`next/og`)는 실험 기능인 `tw` prop으로 Tailwind 클래스를 읽는다. 앱의 `@theme`은 모르므로 색은 임의 값으로 쓴다.
    // 브랜드 규칙: 단색 면. 그라데이션 배경은 쓰지 않는다.
    // Satori `tw`에는 gap 유틸리티가 없어 간격은 두 번째 자식의 margin으로 준다(렌더 픽셀 동일 확인).
    <div tw="flex h-full w-full items-stretch justify-between bg-white p-[80px] font-sans text-[#131e4f]">
      <div tw="flex flex-col justify-between">
        <div tw="flex text-[46px] font-extrabold tracking-[-1px]">
          Go<span tw="text-[#1d4ed8]">Le</span>
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

      {/* 승인된 세 층 파랑·골드 스터드·관절 꼬리를 앱 아이콘과 같은 정본으로 그린다. */}
      <div tw="flex items-center pb-[24px]">
        <svg
          width="420"
          height={(420 * ASSEMBLED_MARK_VIEW_BOX[3]) / ASSEMBLED_MARK_VIEW_BOX[2]}
          viewBox={ASSEMBLED_MARK_VIEW_BOX.join(" ")}
          fill="none"
        >
          {/* Satori는 svg 안에서 함수 컴포넌트를 해석하지 않으므로 순수 렌더러를 먼저 평가한다. */}
          {AssembledMark({})}
        </svg>
      </div>
    </div>,
    size,
  );
}
