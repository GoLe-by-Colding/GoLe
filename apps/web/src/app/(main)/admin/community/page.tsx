import type { Metadata } from "next";
import { AdminCommunityView } from "@views/admin";

export const metadata: Metadata = { title: "커뮤니티" };

export default function Page() {
  return <AdminCommunityView />;
}
