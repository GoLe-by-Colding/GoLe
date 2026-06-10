import type { Metadata } from "next";
import { AuthPage } from "@views/auth";

export const metadata: Metadata = {
  title: "회원가입",
  robots: { index: false, follow: false },
};

export default async function Page({
  searchParams,
}: {
  readonly searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const sp = await searchParams;
  return <AuthPage welcome={sp.welcome === "1"} />;
}
