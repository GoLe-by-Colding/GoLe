import { SearchPage } from "@views/search";

// 활성 리스팅을 매 요청 시 백엔드에서 조회하므로 동적 렌더링한다.
export const dynamic = "force-dynamic";

export default function Page() {
  return <SearchPage />;
}
