import type { Metadata } from "next";
import { SearchPage } from "@views/search";

export const metadata: Metadata = {
  title: "브릭 매물 검색",
  description:
    "상태·가격·테마로 브릭 중고 매물을 검색하세요. 미개봉 새상품부터 조립완성품까지 판매자와 직접 대화할 수 있습니다.",
  alternates: { canonical: "/search" },
  openGraph: {
    title: "브릭 매물 검색 · GoLe",
    description:
      "상태·가격·테마로 브릭 중고 매물을 검색하세요. 미개봉 새상품부터 조립완성품까지 판매자와 직접 대화할 수 있습니다.",
    url: "/search",
    type: "website",
  },
};

// 활성 리스팅을 매 요청 시 백엔드에서 조회하므로 동적 렌더링한다.
export const dynamic = "force-dynamic";

export default async function Page({
  searchParams,
}: {
  readonly searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const sp = await searchParams;
  const first = (v: string | string[] | undefined): string | undefined =>
    Array.isArray(v) ? v[0] : v;

  return (
    <SearchPage
      query={first(sp["query"])}
      condition={first(sp["condition"])}
      category={first(sp["category"])}
      minPrice={first(sp["minPrice"])}
      maxPrice={first(sp["maxPrice"])}
      sort={first(sp["sort"])}
    />
  );
}
