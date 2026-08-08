"use client";

import { useEffect } from "react";
import { Button, LinkButton, Logo } from "@shared/ui";

/**
 * 전역 에러 바운더리. 렌더/데이터 예외 발생 시 표시되며 reset()으로 재시도한다.
 */
export default function Error({
  error,
  reset,
}: {
  readonly error: Error & { readonly digest?: string };
  readonly reset: () => void;
}) {
  useEffect(() => {
    // 운영 환경에서는 모니터링으로 전송할 수 있다(현재는 콘솔).
    console.error(error);
  }, [error]);

  return (
    <main className="grid min-h-dvh place-items-center bg-neutral-50 px-6">
      <div className="flex flex-col items-center gap-6 text-center">
        <Logo size={48} showWordmark={false} />
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-bold text-neutral-900">문제가 발생했어요</h1>
          <p className="max-w-[40ch] text-neutral-500">
            일시적인 오류일 수 있어요. 다시 시도하거나 홈으로 이동해 주세요.
          </p>
        </div>
        <div className="mt-2 flex flex-wrap justify-center gap-3">
          <Button onClick={reset}>다시 시도</Button>
          <LinkButton href="/" variant="secondary">
            홈으로
          </LinkButton>
        </div>
      </div>
    </main>
  );
}
