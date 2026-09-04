import type { Metadata } from "next";
import { CommunityPage } from "@views/community";

export const metadata: Metadata = {
  title: "브릭 커뮤니티",
  description:
    "브릭 빌더들의 자랑과 MOC(My Own Creation) 창작물을 공유하는 커뮤니티. 좋아요와 댓글로 소통하세요.",
  alternates: { canonical: "/community" },
  openGraph: {
    title: "브릭 커뮤니티 · GoLe",
    description:
      "브릭 빌더들의 자랑과 MOC(My Own Creation) 창작물을 공유하는 커뮤니티. 좋아요와 댓글로 소통하세요.",
    url: "/community",
    type: "website",
  },
};

export const dynamic = "force-dynamic";

export default function Page() {
  return <CommunityPage />;
}
