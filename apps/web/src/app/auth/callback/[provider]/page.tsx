import { Suspense } from "react";
import type { Metadata } from "next";
import { OAuthCallbackPage } from "@views/oauth-callback";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "소셜 로그인",
  robots: { index: false, follow: false },
};

export default async function Page({
  params,
}: {
  readonly params: Promise<{ readonly provider: string }>;
}) {
  const { provider } = await params;
  return (
    <Suspense fallback={null}>
      <OAuthCallbackPage provider={provider} />
    </Suspense>
  );
}
