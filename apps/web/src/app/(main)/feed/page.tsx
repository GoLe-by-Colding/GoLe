import type { Metadata } from "next";
import { FollowingFeedPage } from "@views/following-feed";

export const metadata: Metadata = {
  title: "팔로잉 피드",
  description: "팔로우한 빌더와 판매자의 새 글과 매물을 한곳에서 확인하세요.",
  robots: { index: false, follow: true },
};

export default function Page() {
  return <FollowingFeedPage />;
}
