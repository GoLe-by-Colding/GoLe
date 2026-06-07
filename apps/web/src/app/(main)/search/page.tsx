import { SearchPage } from "@views/search";

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
      minPrice={first(sp["minPrice"])}
      maxPrice={first(sp["maxPrice"])}
      sort={first(sp["sort"])}
    />
  );
}
