import type { Metadata } from "next";
import { PartRequestComposePage } from "@views/part-request-compose";

export const metadata: Metadata = {
  title: "부품 요청하기",
  robots: { index: false, follow: false },
};

export default async function Page({
  searchParams,
}: {
  readonly searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const requestedSet = params["set"];
  return (
    <PartRequestComposePage
      initialSetNumber={Array.isArray(requestedSet) ? requestedSet[0] : requestedSet}
    />
  );
}
