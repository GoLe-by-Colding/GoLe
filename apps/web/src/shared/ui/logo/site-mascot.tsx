import type { ReactNode } from "react";
import { fetchActiveMascot } from "@gole/core/mascot";
import { MascotProvider } from "./mascot-context";

/** 적용된 마스코트를 다른 방문자에게 보이기까지의 최대 지연(초). 웹 서버 fetch 캐시다(mascot-assets R4.2). */
const MASCOT_REVALIDATE_SECONDS = 60;

/**
 * 서버 컴포넌트 — 적용된 마스코트를 받아 첫 HTML 부터 그 그림을 그리게 한다. RootLayout 이 감싼다.
 * 조회에 실패하면 기본 고래다.
 */
export async function SiteMascot({ children }: { readonly children: ReactNode }) {
  const active = await fetchActiveMascot({ revalidate: MASCOT_REVALIDATE_SECONDS });
  return <MascotProvider initial={active}>{children}</MascotProvider>;
}
