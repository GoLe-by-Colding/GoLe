"use client";

import { useSyncExternalStore } from "react";
import { formatRequestedAgo } from "../lib/format";

function subscribe(): () => void {
  return () => undefined;
}

export interface RequestedAgoProps {
  /** ISO-8601 시각. */
  readonly iso: string;
  readonly className?: string;
}

/**
 * 요청 시각을 "3시간 전"·"2026.10.01"로 쓴다.
 *
 * 서버 시계·시간대(UTC)와 브라우저(KST)가 달라 서버 렌더 글자가 하이드레이션에서 어긋나므로,
 * 서버 스냅샷에서는 비워 두고 브라우저에서만 채운다. `dateTime` 속성은 처음부터 있다.
 */
export function RequestedAgo({ iso, className }: RequestedAgoProps) {
  const hydrated = useSyncExternalStore(
    subscribe,
    () => true,
    () => false,
  );
  return (
    <time dateTime={iso} className={className}>
      {hydrated ? formatRequestedAgo(iso) : ""}
    </time>
  );
}
