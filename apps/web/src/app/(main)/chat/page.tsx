import type { Metadata } from "next";
import { ChatListPage } from "@views/chat-list";

export const metadata: Metadata = {
  title: "채팅",
  robots: { index: false, follow: false },
};

export default function Page() {
  return <ChatListPage />;
}
