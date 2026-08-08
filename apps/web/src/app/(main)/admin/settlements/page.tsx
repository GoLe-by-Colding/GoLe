import type { Metadata } from "next";
import { AdminSettlementsView } from "@views/admin";

export const metadata: Metadata = { title: "정산" };

export default function Page() {
  return <AdminSettlementsView />;
}
