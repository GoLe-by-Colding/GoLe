"use client";

import type { ReactNode } from "react";
import { useSession } from "@entities/user";

export interface ListingViewerSwitchProps {
  readonly sellerId: string;
  /** 판매자 본인에게만 보일 내용. */
  readonly seller?: ReactNode;
  /** 판매자가 아닌 사람(비로그인 포함)에게 보일 내용. */
  readonly visitor?: ReactNode;
}

/**
 * 상세 화면은 세션 없이 서버에서 그려진다. 본인 매물 여부는 브라우저 세션으로만 알 수 있으므로
 * 이 경계에서 갈라 그린다. 서버 렌더·하이드레이션 동안은 방문자 쪽을 그린다.
 */
export function ListingViewerSwitch({ sellerId, seller, visitor }: ListingViewerSwitchProps) {
  const { session } = useSession();
  return <>{session?.accountId === sellerId ? (seller ?? null) : (visitor ?? null)}</>;
}
