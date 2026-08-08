import type { Metadata } from "next";
import { AdminCatalogView } from "@views/admin";

export const metadata: Metadata = { title: "카탈로그" };

export default function Page() {
  return <AdminCatalogView />;
}
