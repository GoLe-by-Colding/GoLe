import type { Metadata } from "next";
import { HomePage } from "@views/home";

export const metadata: Metadata = {
  alternates: { canonical: "/" },
};

// 홈은 추천 세트를 매 요청 시 백엔드에서 조회하므로 동적 렌더링한다.
export const dynamic = "force-dynamic";

// FSD: app 레이어의 라우트는 얇게 유지하고 view를 조합만 한다.
export default function Page() {
  return <HomePage />;
}
