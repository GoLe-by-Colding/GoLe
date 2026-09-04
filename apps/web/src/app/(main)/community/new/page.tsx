import type { Metadata } from "next";
import { CommunityComposePage } from "@views/community-compose";

export const metadata: Metadata = {
  title: "새 글 작성",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <CommunityComposePage />;
}
