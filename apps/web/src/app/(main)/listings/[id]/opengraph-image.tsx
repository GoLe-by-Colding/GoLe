import { ImageResponse } from "next/og";
import { env } from "@shared/config";

export const alt = "GoLe 상품";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default async function OpengraphImage({
  params,
}: {
  readonly params: Promise<{ readonly id: string }>;
}) {
  const { id } = await params;

  // 백엔드에서 매물 정보를 가져온다. 실패 시 기본 OG 폴백.
  let title = "GoLe 브릭 매물";
  let price = "";
  let category = "";
  try {
    const res = await fetch(`${env.apiBaseUrl}/api/v1/listings/${id}`, {
      next: { revalidate: 60 },
    });
    if (res.ok) {
      const data = (await res.json()) as {
        title?: string;
        price?: number;
        category?: string;
      };
      if (data.title) title = data.title;
      if (data.price) price = `₩${data.price.toLocaleString("ko-KR")}`;
      if (data.category) category = data.category.toUpperCase();
    }
  } catch {
    // 폴백
  }

  return new ImageResponse(
    // Satori(`next/og`)는 실험 기능인 `tw` prop으로 Tailwind 클래스를 읽는다. 앱의 `@theme`은 모르므로 색은 임의 값으로 쓴다.
    // 그라데이션만 `style`에 둔다. Satori `tw`는 임의 값·from/to 그라데이션을 모두 무시한다(2026-10-11 next 16.3.8에서 렌더해 확인).
    // gap 유틸리티도 없어 간격은 뒤 자식의 margin으로 준다(렌더 픽셀 동일 확인).
    <div
      tw="flex h-full w-full flex-col justify-between p-[72px] font-sans text-white"
      style={{ background: "linear-gradient(135deg, #1d4ed8 0%, #1c2f7c 100%)" }}
    >
      <div tw="flex items-center">
        <div tw="text-[32px] font-extrabold">GoLe</div>
        {category ? (
          <div tw="ml-[16px] rounded-[8px] bg-white/20 px-[12px] py-[4px] text-[22px]">
            {category}
          </div>
        ) : null}
      </div>

      <div tw="flex flex-col">
        <div tw="text-[64px] font-extrabold leading-[1.1]">{title}</div>
        {price ? <div tw="mt-[16px] text-[48px] font-bold text-[#facc15]">{price}</div> : null}
      </div>

      <div tw="text-[24px] opacity-70">gole.co.kr</div>
    </div>,
    size,
  );
}
