import type { Metadata } from "next";
import { AdminExceptionsView } from "@views/admin";

export const metadata: Metadata = { title: "예외 큐" };

export default function Page() {
  return <AdminExceptionsView />;
}
