import type { Metadata } from "next";
import { AdminListingsView } from "@views/admin";

export const metadata: Metadata = { title: "매물" };

export default function Page() {
  return <AdminListingsView />;
}
