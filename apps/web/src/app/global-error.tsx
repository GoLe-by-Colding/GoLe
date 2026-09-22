"use client";

import { captureMessage } from "@sentry/nextjs";
import { useEffect } from "react";

/** 루트 레이아웃 오류도 원문 없이 수집한다. */
export default function GlobalError({
  error,
  reset,
}: {
  readonly error: Error;
  readonly reset: () => void;
}) {
  useEffect(() => {
    captureMessage("UNEXPECTED_ERROR", "error");
  }, [error]);
  return (
    <html lang="ko">
      <body>
        <main>
          <h1>문제가 발생했어요</h1>
          <p>잠시 후 다시 시도해 주세요.</p>
          <button onClick={reset}>다시 시도</button>
        </main>
      </body>
    </html>
  );
}
